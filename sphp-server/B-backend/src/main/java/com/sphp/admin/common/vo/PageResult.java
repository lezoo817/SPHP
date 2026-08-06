package com.sphp.admin.common.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应封装：{@code total / list / page / size}。
 *
 * @param <T> 列表元素类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "当前页数据")
    private List<T> list;

    @Schema(description = "页码（从 1 开始）")
    private long page;

    @Schema(description = "每页大小")
    private long size;

    /**
     * 构造分页响应。
     *
     * @param total 总记录数
     * @param list  当前页数据
     * @param page  当前页码（从 1 开始）
     * @param size  每页大小
     * @param <T>   列表元素类型
     * @return 完整的分页响应对象
     */
    public static <T> PageResult<T> of(long total, List<T> list, long page, long size) {
        return PageResult.<T>builder()
                .total(total)
                .list(list)
                .page(page)
                .size(size)
                .build();
    }
}
