package com.cardwiz.userservice.controllers;

import com.cardwiz.userservice.dtos.AdvisorMessageCreateRequest;
import com.cardwiz.userservice.dtos.AdvisorMessageResponse;
import com.cardwiz.userservice.dtos.UserResponseDTO;
import com.cardwiz.userservice.services.AdvisorHistoryService;
import com.cardwiz.userservice.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/advisor")
@RequiredArgsConstructor
public class AdvisorController {

    private final AdvisorHistoryService advisorHistoryService;
    private final UserService userService;

    @GetMapping("/history")
    public ResponseEntity<List<AdvisorMessageResponse>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(advisorHistoryService.getHistory(Long.valueOf(current.getId())));
    }

    @PostMapping("/history")
    public ResponseEntity<AdvisorMessageResponse> saveMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AdvisorMessageCreateRequest request
    ) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(advisorHistoryService.saveMessage(Long.valueOf(current.getId()), request));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        advisorHistoryService.clearHistory(Long.valueOf(current.getId()));
        return ResponseEntity.noContent().build();
    }
}
