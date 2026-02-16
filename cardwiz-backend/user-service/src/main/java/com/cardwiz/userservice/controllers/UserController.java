package com.cardwiz.userservice.controllers;

import com.cardwiz.userservice.dtos.ChangePasswordRequest;
import com.cardwiz.userservice.dtos.UserResponseDTO;
import com.cardwiz.userservice.dtos.UserUpdateRequest;
import com.cardwiz.userservice.services.ImageUploadService;
import com.cardwiz.userservice.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ImageUploadService imageUploadService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserProfileByEmail(userDetails.getUsername()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserUpdateRequest request) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(userService.updateUserProfile(Long.valueOf(current.getId()), request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChangePasswordRequest request) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        userService.changePassword(Long.valueOf(current.getId()), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upload-image")
    public ResponseEntity<UserResponseDTO> uploadProfileImage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        String imageKey = imageUploadService.uploadProfileImage(file, Long.valueOf(current.getId()));
        return ResponseEntity.ok(userService.updateProfileImage(Long.valueOf(current.getId()), imageKey));
    }

    @GetMapping("/me/profile-image")
    public ResponseEntity<String> getProfileImageUrl(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(userService.getProfileImageUrl(Long.valueOf(current.getId())));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        userService.deleteUser(Long.valueOf(current.getId()));
        return ResponseEntity.noContent().build();
    }
}
