package com.nemosdream.be.bandit.dto;

import java.util.List;

public record BanditRecommendationResponse(String modelVersion,List<BanditRanking> rankings) {}
