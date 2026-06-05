package com.recycle.bidding.common.result;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应体
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码
     */
    private int page;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 数据列表
     */
    private List<T> records;

    public PageResult() {
        this.records = Collections.emptyList();
    }

    public PageResult(long total, int page, int size, List<T> records) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.records = records != null ? records : Collections.emptyList();
    }

    /**
     * 创建空分页结果
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(0, page, size, Collections.emptyList());
    }

    // --- Getters and Setters ---

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records != null ? records : Collections.emptyList();
    }

    @Override
    public String toString() {
        return "PageResult{" +
                "total=" + total +
                ", page=" + page +
                ", size=" + size +
                ", records.size=" + (records != null ? records.size() : 0) +
                '}';
    }
}
