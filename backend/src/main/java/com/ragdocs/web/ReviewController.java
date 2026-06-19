package com.ragdocs.web;

import com.ragdocs.auth.CurrentUser;
import com.ragdocs.auth.JwtAuthenticationFilter;
import com.ragdocs.common.ApiResponse;
import com.ragdocs.rag.ReviewService;
import com.ragdocs.web.dto.CreateReviewRequest;
import com.ragdocs.web.dto.ReviewDto;
import com.ragdocs.web.dto.ReviewTypeDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/types")
    public ApiResponse<List<ReviewTypeDto>> types() {
        return ApiResponse.ok(reviewService.reviewTypes());
    }

    @GetMapping
    public ApiResponse<List<ReviewDto>> list(@RequestParam(required = false) Long kbId, HttpServletRequest request) {
        return ApiResponse.ok(reviewService.listReviews(currentUser(request).id(), kbId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewDto> get(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(reviewService.getReview(currentUser(request).id(), id));
    }

    @PostMapping
    public ApiResponse<ReviewDto> create(@Valid @RequestBody CreateReviewRequest body, HttpServletRequest request) {
        return ApiResponse.ok(reviewService.createReview(currentUser(request).id(), body));
    }

    private CurrentUser currentUser(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(JwtAuthenticationFilter.CURRENT_USER_ATTRIBUTE);
    }
}
