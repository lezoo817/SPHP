package com.sphp.patient.consultation.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 预问诊附件展示项。
 */
@Getter
@Setter
@NoArgsConstructor
public class ConsultationAttachmentVO {

    /** 附件展示名称 */
    private String name;
    /** 附件访问地址 */
    private String url;
}
