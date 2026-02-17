💳 CardWiz Frontend

The AI-Powered Financial Command Center

CardWiz Frontend is a responsive, modern web application built with React and Tailwind CSS. It serves as the visual interface for users to manage their credit cards and interact with the Amazon Nova multimodal AI agents.
🛠 Tech Stack
🎨 UI/UX Design Philosophy

    Trust & Clarity: Use a clean color palette (Deep Blues, Crisp Whites, Success Greens) to convey financial stability.

    AI Transparency: When the AI is "thinking" (Nova 2 Pro reasoning), show a distinct loading state so the user knows complex analysis is happening.

    Multimodal First: The upload zones for images/PDFs are not hidden in menus; they are front-and-center features.

🧩 Must-Have Components
1. SmartAdvisor (The "Magic" Input)

This is the core feature where users ask, "Where should I use my card?"

    Function: Accepts text input or voice (future).

    Visuals: A chat-like interface. When a recommendation arrives, it highlights the "Winner" card with a glowing border.

    Props: userCards (context), onRecommend (callback).

    AI State: Must handle isAnalyzing state to show a "Consulting financial models..." skeleton loader.

2. MultimodalUpload (The Document Eye)

The interface for uploading bank statements or reward brochures.

    Function: Drag-and-drop zone for PDF/Images.

    Features:

        Preview: Shows a thumbnail of the uploaded image.

        Extraction View: Once processed, it transforms the raw JSON response from the backend into a clean "Reward Rules Table" (e.g., "5% Cashback on Amazon").

        Confidence Badge: Shows "High Confidence" if Nova is sure, or "Review Needed" if the document was blurry.

3. RewardCard (The Display Unit)

A reusable card component that displays credit card details.

    Variants:

        Standard: Just the card art, name, and last 4 digits.

        Recommendation: Includes a "Why this card?" text block (Markdown rendered) and a calculated "Estimated Reward" (e.g., "+₹150 Cashback").

    Visuals: Uses CSS gradients to mimic the physical look of cards (Gold, Platinum, etc.).

4. AuthGuard (Security Wrapper)

A Higher-Order Component (HOC) or Wrapper that checks for a valid JWT in localStorage.

    Logic: If no token exists, redirect to /login. If token is expired, trigger refresh or logout.

    Integration: Wraps all private routes (/dashboard, /advisor, /profile).

📂 Project Structure

We use a Feature-based folder structure to keep logic bundled with UI.
🔌 API Integration Strategy
The axiosClient.js

Do not use fetch directly in components. Use a configured instance to handle Auth headers automatically.
🚀 Getting Started

    Install Dependencies:

    Environment Setup (.env):

    Run Development Server: