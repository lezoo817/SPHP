package com.sphp.patient.common.constant;

/**
 * 健康报告、用药与随访模块的公共常量。
 */
public final class ProposalConstant {

    /** 报告列表默认页码。 */
    public static final int DEFAULT_PAGE_NO = 1;

    /** 报告列表默认每页数量。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 报告列表允许的最大每页数量，防止大分页查询。 */
    public static final int MAX_PAGE_SIZE = 100;


    /**
     * 禁止实例化常量类。
     */
    private ProposalConstant() {
    }
}
