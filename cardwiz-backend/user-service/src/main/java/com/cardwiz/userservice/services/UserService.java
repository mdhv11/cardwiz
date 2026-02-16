package com.cardwiz.userservice.services;

import com.cardwiz.userservice.dtos.UserResponseDTO;
import com.cardwiz.userservice.dtos.UserUpdateRequest;
import com.cardwiz.userservice.dtos.ChangePasswordRequest;
import com.cardwiz.userservice.models.Block;
import com.cardwiz.userservice.models.Follow;
import com.cardwiz.userservice.models.User;
import com.cardwiz.userservice.repositories.BlockRepository;
import com.cardwiz.userservice.repositories.FollowRepository;
import com.cardwiz.userservice.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageUploadService imageUploadService;
    private final BlockListCacheService blockListCacheService;

    /*
     * Logic for User A following User B
     */
    @Transactional
    public void followUser(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("You cannot follow yourself");
        }

        boolean isBlocked = blockListCacheService.isBlockedEitherWay(followerId, followingId);

        if (isBlocked) {
            throw new RuntimeException("Action not allowed. You are blocked or have blocked this user.");
        }

        // 2. Check if already following (Idempotency)
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            return;
        }

        // 3. Get User Proxies
        // We use getReferenceById assuming IDs are valid.
        // If invalid, Hibernate will throw EntityNotFoundException when we try to save.
        User follower = userRepository.getReferenceById(followerId);
        User following = userRepository.getReferenceById(followingId);

        // 4. Save relationship
        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowing(following);
        followRepository.save(follow);
    }

    @Transactional
    public void unfollowUser(Long followerId, Long followingId) {
        followRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
    }

    public boolean isFollowing(Long followerId, Long followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    @Transactional
    public com.cardwiz.userservice.dtos.BlockStatusResponse blockUser(Long blockerId, Long blockedId) {

        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("You cannot block yourself");
        }

        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            blockListCacheService.addBlock(blockerId, blockedId);
            return com.cardwiz.userservice.dtos.BlockStatusResponse.builder()
                    .status("BLOCKED")
                    .blockerId(blockerId)
                    .targetId(blockedId)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();
        }

        User blocker = userRepository.getReferenceById(blockerId);
        User blocked = userRepository.getReferenceById(blockedId);

        // 1. Save block
        Block block = new Block();
        block.setBlocker(blocker);
        block.setBlocked(blocked);
        blockRepository.save(block);

        // 2. Remove follow relationships BOTH ways
        followRepository.deleteBidirectionalFollow(blockerId, blockedId);
        blockListCacheService.addBlock(blockerId, blockedId);

        return com.cardwiz.userservice.dtos.BlockStatusResponse.builder()
                .status("BLOCKED")
                .blockerId(blockerId)
                .targetId(blockedId)
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    @Transactional
    public com.cardwiz.userservice.dtos.BlockStatusResponse unblockUser(Long blockerId, Long blockedId) {
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
        blockListCacheService.removeBlock(blockerId, blockedId);
        return com.cardwiz.userservice.dtos.BlockStatusResponse.builder()
                .status("UNBLOCKED")
                .blockerId(blockerId)
                .targetId(blockedId)
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    @Transactional
    public void muteUser(Long followerId, Long followingId) {

        Follow follow = followRepository
                .findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseThrow(() -> new RuntimeException("You are not following this user"));

        follow.setMuted(true);
    }

    @Transactional
    public void unmuteUser(Long followerId, Long followingId) {

        Follow follow = followRepository
                .findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseThrow(() -> new RuntimeException("You are not following this user"));

        follow.setMuted(false);
    }

    // pagination for "Who follows this user?"
    public Page<Long> getFollowers(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdOn"));

        // Much faster: DB returns numbers only, no object mapping needed
        return followRepository.findFollowerIds(userId, pageable);
    }

    // Pagination for "Who is this user following?"
    public Page<Long> getFollowing(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdOn"));
        return followRepository.findFollowingIds(userId, pageable);
    }

    /**
     * Get all users with pagination
     */
    public Page<UserResponseDTO> getAllUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdOn"));
        Page<User> users = userRepository.findAll(pageable);

        return users.map(user -> UserResponseDTO.builder()
                .id(String.valueOf(user.getId()))
                .name(user.getName())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .profileImageURL(resolveProfileImageUrl(user.getProfileImageURL()))
                .karma(user.getKarma())
                .followerCount(0) // Simplified for now
                .followingCount(0) // Simplified for now
                .showNSFW(user.isShowNSFW())
                .showFollowedOnly(user.isShowFollowedOnly())
                .emailNotifications(user.isEmailNotifications())
                .pushNotifications(user.isPushNotifications())
                .followers(List.of()) // Simplified for now
                .following(List.of()) // Simplified for now
                .build());
    }

    /**
     * Fetch User and assemble the JSON structure frontend expects
     */
    /**
     * Fetch User and assemble the JSON structure frontend expects
     */
    public UserResponseDTO getUserProfile(Long userId) {
        System.out.println("DEBUG: getUserProfile called for userId: " + userId);

        // 1. Fetch the User Entity
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Fetch just the IDs for the lists
        List<Long> followerIds = followRepository.findFollowerIdsByUserId(userId);
        List<Long> followingIds = followRepository.findFollowingIdsByUserId(userId);
        long followerCount = followRepository.countByFollowingId(userId);
        long followingCount = followRepository.countByFollowerId(userId);

        System.out
                .println("DEBUG: Fetched user details. Followers: " + followerCount + ", Following: " + followingCount);

        // 3. Map to DTO
        return UserResponseDTO.builder()
                .id(String.valueOf(user.getId()))
                .name(user.getName())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .profileImageURL(resolveProfileImageUrl(user.getProfileImageURL()))
                .karma(user.getKarma())
                .followerCount((int) followerCount)
                .followingCount((int) followingCount)
                .showNSFW(user.isShowNSFW())
                .showFollowedOnly(user.isShowFollowedOnly())
                .emailNotifications(user.isEmailNotifications())
                .pushNotifications(user.isPushNotifications())
                // Convert Long lists to String lists
                .followers(followerIds.stream().map(String::valueOf).collect(Collectors.toList()))
                .following(followingIds.stream().map(String::valueOf).collect(Collectors.toList()))
                .build();
    }

    /**
     * Update user profile information
     */
    @Transactional
    public UserResponseDTO updateUserProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        user.setShowNSFW(request.isShowNSFW());
        user.setShowFollowedOnly(request.isShowFollowedOnly());
        user.setEmailNotifications(request.isEmailNotifications());
        user.setPushNotifications(request.isPushNotifications());

        User updatedUser = userRepository.save(user);
        return getUserProfile(updatedUser.getId());
    }

    /**
     * Change user password
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Verify new password matches confirmation
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New passwords do not match");
        }

        // Validate new password is not empty and different from current
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new RuntimeException("New password cannot be empty");
        }

        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new RuntimeException("New password must be different from current password");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Update user profile image
     */
    @Transactional
    public UserResponseDTO updateProfileImage(Long userId, String imageKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (imageKey == null || imageKey.isBlank()) {
            throw new RuntimeException("Image key cannot be empty");
        }

        user.setProfileImageURL(imageKey);
        User updatedUser = userRepository.save(user);
        return getUserProfile(updatedUser.getId());
    }

    public String getProfileImageUrl(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return resolveProfileImageUrl(user.getProfileImageURL());
    }

    private String resolveProfileImageUrl(String imageKey) {
        return imageUploadService.getImageUrl(imageKey);
    }
}
