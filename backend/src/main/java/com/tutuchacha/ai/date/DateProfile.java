package com.tutuchacha.ai.date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 恋爱助手：我的档案，一个用户一份。 */
@Data
@TableName("date_profile")
public class DateProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String nickname;

    private String gender;

    private Integer age;

    private String city;

    private String occupation;

    private String hobbies;

    private String personality;

    private String goal;

    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
