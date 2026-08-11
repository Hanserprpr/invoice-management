package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    @Select("SELECT * FROM role WHERE code = #{code}")
    Role findByCode(@Param("code") String code);
}
