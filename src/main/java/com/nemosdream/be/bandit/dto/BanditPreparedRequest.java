package com.nemosdream.be.bandit.dto;

import java.util.Map;
import java.util.Set;

public record BanditPreparedRequest(Map<String,Object> request, Set<String> candidateIds) {}
