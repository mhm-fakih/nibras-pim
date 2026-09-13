package com.nibras.pim.web.dto;

/** How much of the job is left, and how much has been set aside. */
public record FlashCardSummary(long total, long remaining, long reviewed, long repeat, long kept) {}
