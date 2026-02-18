import re

from app.services.embedding_service import EmbeddingService


def _to_float(value):
    try:
        if value is None:
            return None
        return float(value)
    except Exception:
        return None


def _extract(pattern: str, content_text: str):
    match = re.search(pattern, content_text or "", re.IGNORECASE)
    return match.group(1).strip() if match else None


def parse_rule_payload(content_text: str, fallback_card_name: str) -> dict:
    reward_type = (_extract(r"reward_type\s*=\s*([A-Za-z_]+)", content_text) or "UNKNOWN").upper()
    reward_rate = _to_float(_extract(r"reward_rate\s*=\s*([0-9]+(?:\.[0-9]+)?)", content_text))
    effective_pct = _to_float(_extract(r"effective_reward_percentage\s*=\s*([0-9]+(?:\.[0-9]+)?)", content_text))
    points_per_unit = _to_float(_extract(r"points_per_unit\s*=\s*([0-9]+(?:\.[0-9]+)?)", content_text))
    spend_unit = _to_float(_extract(r"spend_unit\s*=\s*([0-9]+(?:\.[0-9]+)?)", content_text))
    point_value = _to_float(_extract(r"point_value_rupees\s*=\s*([0-9]+(?:\.[0-9]+)?)", content_text))
    card_name = _extract(r"card_name\s*=\s*([^;]+)", content_text) or fallback_card_name

    if effective_pct is None:
        if reward_type == "CASHBACK" and reward_rate is not None:
            effective_pct = reward_rate
        elif reward_type == "POINTS" and points_per_unit is not None and spend_unit and spend_unit > 0:
            effective_pct = (points_per_unit * (point_value if point_value is not None else 0.25) / spend_unit) * 100.0

    return {
        "card_name": card_name.strip(),
        "reward_type": reward_type,
        "reward_rate": reward_rate if reward_rate is not None else 0.0,
        "effective_reward_percentage": effective_pct if effective_pct is not None else 0.0,
        "points_per_unit": points_per_unit,
        "spend_unit": spend_unit,
        "point_value": point_value if point_value is not None else 0.25,
    }


def calculate_reward(
    spend_amount: float,
    reward_rate: float,
    point_value: float = 0.25,
    reward_mode: str = "PERCENTAGE",
) -> float:
    """
    Calculates exact reward value in INR.
    reward_mode:
    - PERCENTAGE: reward_rate is a percentage (e.g., 5 for 5%)
    - POINTS_PER_RUPEE: reward_rate is points earned per 1 INR spent
    """
    spend = max(0.0, float(spend_amount or 0.0))
    rate = max(0.0, float(reward_rate or 0.0))
    value_per_point = max(0.0, float(point_value or 0.25))
    mode = (reward_mode or "PERCENTAGE").upper()

    if mode == "POINTS_PER_RUPEE":
        return round(spend * rate * value_per_point, 2)
    return round(spend * (rate / 100.0), 2)


async def search_card_rules(
    embedding_service: EmbeddingService,
    merchant: str,
    category: str | None,
    user_id: int,
    available_card_ids: list[int],
    top_k: int = 10,
) -> list[dict]:
    """
    Tool wrapper for hybrid vector + keyword retrieval with card filtering.
    """
    query_text = f"{merchant or ''} {category or ''}".strip()
    candidates = await embedding_service.search_similar_rules(query_text=query_text, top_k=top_k)
    allowed = {int(card_id) for card_id in available_card_ids or []}
    filtered = [row for row in candidates if int(row.get("card_id", -1)) in allowed]

    result = []
    for row in filtered:
        card_id = int(row["card_id"])
        parsed = parse_rule_payload(row.get("content_text", ""), f"Card {card_id}")
        result.append(
            {
                "rule_id": int(row["rule_id"]),
                "card_id": card_id,
                "card_name": parsed["card_name"],
                "reward_type": parsed["reward_type"],
                "effective_reward_percentage": round(float(parsed["effective_reward_percentage"]), 4),
                "point_value": round(float(parsed["point_value"]), 4),
                "vector_score": float(row.get("vector_score", 0.0)),
                "text_score": float(row.get("text_score", 0.0)),
                "final_score": float(row.get("final_score", 0.0)),
            }
        )
    return result
