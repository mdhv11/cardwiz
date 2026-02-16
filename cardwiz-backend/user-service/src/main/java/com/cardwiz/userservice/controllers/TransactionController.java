package com.cardwiz.userservice.controllers;

import com.cardwiz.userservice.dtos.TransactionRequest;
import com.cardwiz.userservice.dtos.TransactionResponse;
import com.cardwiz.userservice.dtos.UserResponseDTO;
import com.cardwiz.userservice.services.TransactionService;
import com.cardwiz.userservice.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> listTransactions(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(transactionService.listTransactions(Long.valueOf(current.getId())));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long transactionId) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(transactionService.getTransaction(Long.valueOf(current.getId()), transactionId));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TransactionRequest request) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(transactionService.createTransaction(Long.valueOf(current.getId()), request));
    }

    @PutMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long transactionId,
            @RequestBody TransactionRequest request) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(transactionService.updateTransaction(Long.valueOf(current.getId()), transactionId, request));
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long transactionId) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        transactionService.deleteTransaction(Long.valueOf(current.getId()), transactionId);
        return ResponseEntity.noContent().build();
    }
}
