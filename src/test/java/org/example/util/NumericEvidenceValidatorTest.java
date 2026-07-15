package org.example.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NumericEvidenceValidatorTest {

    @Test
    void numbersPresentInSourceAreVerified() {
        String answer = "该方法的 SR 为 0.92，SPL 为 3.5dB。";
        List<String> sources = List.of("实验结果：SR=0.92，SPL=3.5dB，NE=12");
        List<String> unverified = NumericEvidenceValidator.findUnverified(answer, sources);
        assertTrue(unverified.isEmpty(), "原文中存在的数值不应被标记为未验证: " + unverified);
    }

    @Test
    void fabricatedNumbersAreFlagged() {
        String answer = "该方法的 SR 为 0.95，相比基线提升 +100%。";
        List<String> sources = List.of("实验结果：SR=0.92，SPL=3.5dB");
        List<String> unverified = NumericEvidenceValidator.findUnverified(answer, sources);
        // 0.95 与 100 都不在原文中，应被识别为模型编造/推导
        assertTrue(unverified.contains("0.95"), unverified.toString());
        assertTrue(unverified.contains("100"), unverified.toString());
    }

    @Test
    void derivedPercentageFromTwiceIsFlagged() {
        String answer = "吞吐量变为 2 倍，即减少 50%。";
        List<String> sources = List.of("吞吐量提升至 2 倍");
        List<String> unverified = NumericEvidenceValidator.findUnverified(answer, sources);
        assertTrue(unverified.contains("50"), "由 2× 推导出的 50% 不在原文中，应被标记: " + unverified);
    }

    @Test
    void yearsAreNotTreatedAsExperimentalNumbers() {
        String answer = "该论文发表于 2023 年，SR 为 0.92。";
        List<String> sources = List.of("2023 年发表，SR=0.92");
        List<String> unverified = NumericEvidenceValidator.findUnverified(answer, sources);
        assertFalse(unverified.contains("2023"), "年份不应被当作实验数值校验");
        assertFalse(unverified.contains("0.92") && unverified.contains("0.92"));
    }
}
