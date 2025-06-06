package com.ruoyi.common.enums;

import java.util.HashMap;
import java.util.Map;

public enum OrderTypeEnum {
    Global(0, "Global"),
    General(1, "General"),
    Plumbing(2, "Plumbing"),
    Cement(3, "Cement"),
    Carpentry(4, "Carpentry"),
    Internet(5, "Internet");

    private final int code;
    private final String desc;

    // 静态Map缓存所有枚举值
    private static final Map<Integer, OrderTypeEnum> CODE_MAP = new HashMap<>();

    // 静态初始化块填充Map
    static {
        for (OrderTypeEnum type : OrderTypeEnum.values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    OrderTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static OrderTypeEnum fromCode(int code) {
        return CODE_MAP.getOrDefault(code, General); // 默认返回PLUMBING
    }
}
