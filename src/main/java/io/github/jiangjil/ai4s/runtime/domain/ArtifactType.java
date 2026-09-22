package io.github.jiangjil.ai4s.runtime.domain;

/** 面向人和运行中心展示的产物类型；实际内容始终保存在 URI 指向的持久介质。 */
public enum ArtifactType {
    CODE_DIFF, TEST_REPORT, BUILD_OUTPUT, LOG, DATASET, MODEL, CHECKPOINT, DOCUMENT, OTHER
}
