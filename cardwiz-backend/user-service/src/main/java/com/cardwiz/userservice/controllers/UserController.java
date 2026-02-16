package com.cardwiz.userservice.controllers;

import com.cardwiz.userservice.dtos.UserResponseDTO;
import com.cardwiz.userservice.dtos.UserUpdateRequest;
import com.cardwiz.userservice.dtos.ChangePasswordRequest;
import com.cardwiz.userservice.services.UserService;
import com.cardwiz.userservice.services.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ImageUploadService imageUploadService;

    @GetMapping
    public ResponseEntity<Page<UserResponseDTO>> getAllUsers(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.getAllUsers(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserProfile(id));
    }

    /**
     * Update user profile information
     * @param id User ID
     * @param request UserUpdateRequest containing name, bio, and notification preferences
     * @return Updated user profile
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUserProfile(
            @PathVariable Long id,
            @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUserProfile(id, request));
    }

    /**
     * Update user profile image (via file upload)
     * Uploads image to S3 and stores object key in database
     */
    @PostMapping("/{id}/profile-image/upload")
    public ResponseEntity<UserResponseDTO> uploadProfileImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            String imageKey = imageUploadService.uploadProfileImage(file, id);
            return ResponseEntity.ok(userService.updateProfileImage(id, imageKey));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid image: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to upload image: " + e.getMessage());
        }
    }

    /**
     * Update user profile image using an existing S3 key (e.g., from another service)
     */
    @PutMapping("/{id}/profile-image")
    public ResponseEntity<UserResponseDTO> updateProfileImage(
            @PathVariable Long id,
            @RequestBody String imageKey) {
        return ResponseEntity.ok(userService.updateProfileImage(id, imageKey));
    }

    @GetMapping("/{id}/profile-image/url")
    public ResponseEntity<String> getProfileImageUrl(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getProfileImageUrl(id));
    }

    /**
     * Change user password
     * @param id User ID
     * @param request ChangePasswordRequest containing current password and new password
     * @return 200 OK if successful
     */
    @PostMapping("/{id}/change-password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request);
        return ResponseEntity.ok().build();
    }
}
