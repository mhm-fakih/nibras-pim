package com.nibras.pim.domain;

/**
 * Records that carry a short listing blurb (its {@code short_description} column), used by the
 * static-site generator for the index/summary card. Implemented by writings, notes and resources.
 */
public interface HasShortSummary {

    /** Short summary for listings; may be null/blank (the generator then falls back to the body). */
    String getShortDescription();
}
