package com.tutuchacha.ai.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CosineSimilarityTest {

    @Test
    void identicalVectorsScoreOne() {
        assertThat(CosineSimilarity.between(new float[]{1f, 2f, 3f}, new float[]{1f, 2f, 3f}))
                .isCloseTo(1d, within(1e-9));
    }

    @Test
    void orthogonalVectorsScoreZero() {
        assertThat(CosineSimilarity.between(new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isCloseTo(0d, within(1e-9));
    }

    @Test
    void oppositeVectorsScoreMinusOne() {
        assertThat(CosineSimilarity.between(new float[]{1f, 0f}, new float[]{-1f, 0f}))
                .isCloseTo(-1d, within(1e-9));
    }

    @Test
    void scaleDoesNotChangeSimilarity() {
        assertThat(CosineSimilarity.between(new float[]{1f, 1f}, new float[]{5f, 5f}))
                .isCloseTo(1d, within(1e-9));
    }

    @Test
    void dimensionMismatchOrZeroVectorScoresZero() {
        assertThat(CosineSimilarity.between(new float[]{1f, 0f}, new float[]{1f})).isZero();
        assertThat(CosineSimilarity.between(new float[]{0f, 0f}, new float[]{1f, 1f})).isZero();
        assertThat(CosineSimilarity.between(null, new float[]{1f})).isZero();
        assertThat(CosineSimilarity.between(new float[0], new float[0])).isZero();
    }
}
