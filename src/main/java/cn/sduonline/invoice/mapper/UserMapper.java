package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
