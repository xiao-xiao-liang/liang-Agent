package com.liang.agent.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.agent.model.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息 Mapper 接口
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
