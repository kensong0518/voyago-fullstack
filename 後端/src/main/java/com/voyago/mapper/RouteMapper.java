package com.voyago.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voyago.entity.Route;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行程 Mapper（MyBatis-Plus）。
 * 繼承 BaseMapper 即自帶 selectList / selectPage / selectCount / selectOne 等 CRUD。
 */
@Mapper
public interface RouteMapper extends BaseMapper<Route> {
}
