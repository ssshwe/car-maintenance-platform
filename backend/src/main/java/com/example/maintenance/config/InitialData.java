package com.example.maintenance.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.maintenance.api.Requests.ProductInput;
import com.example.maintenance.auth.Actor;
import com.example.maintenance.entity.*;
import com.example.maintenance.mapper.*;
import com.example.maintenance.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name="app.seed",havingValue="true")
@RequiredArgsConstructor
public class InitialData implements CommandLineRunner {
    private final UserMapper users;
    private final StoreMapper stores;
    private final AuthService auth;
    private final AdministrationService admin;
    @Override @Transactional public void run(String... args) {
        if (users.selectCount(null) == 0) {
            User administrator = auth.createUser("admin","13900000001","Admin@123","ADMIN","ACTIVE");
            auth.createUser("customer","13900000002","User@123","CUSTOMER","ACTIVE");
            createStore("store","13900000003","车途养护 · 龙子湖店","郑州市郑东新区平安大道与龙子湖西路交叉口");
            createStore("store2","13900000004","车途养护 · 金水店","郑州市金水区花园路与农业路交叉口");
            Actor actor = new Actor(administrator.getId(),administrator.getLoginName(),"ADMIN",null);
            String[][] seeds = {
                {"OIL-001","全合成发动机油 5W-30","润滑油","4L装全合成机油，适用于多数汽油车型。到店由技师确认适配性。","298","60","80","oil"},
                {"FILTER-001","高效机油滤清器","滤清器","多层过滤结构，配合换油保养使用，减少杂质进入发动机。","58","20","120","filter"},
                {"AIR-001","发动机空气滤清器","滤清器","稳定进气，过滤灰尘与细小杂质。建议按车型及保养手册更换。","89","20","65","air"},
                {"BRAKE-001","陶瓷刹车片 前轮套装","制动系统","一套前轮刹车片，低噪制动配方，适配车型请到店确认。","368","120","35","brake"},
                {"BATTERY-001","免维护蓄电池 60Ah","电气系统","12V 60Ah免维护蓄电池，安装前由门店检查尺寸与接口。","528","50","28","battery"},
                {"WIPER-001","静音无骨雨刷 双支装","车身附件","柔性贴合玻璃，双支套装，提升雨天驾驶视野。","79","15","90","wiper"},
                {"COOL-001","长效防冻冷却液 4L","润滑油","长效防冻冷却液，使用前由门店确认与原车冷却液兼容。","128","40","55","coolant"},
                {"CABIN-001","活性炭空调滤芯","滤清器","活性炭复合滤层，改善车内空气过滤效果。","99","25","70","air"}
            };
            for (String[] s : seeds) admin.saveProduct(actor,null,new ProductInput(s[0],s[1],s[2],s[3],"/assets/" + s[7] + ".svg",new BigDecimal(s[4]),new BigDecimal(s[5]),"ON_SHELF",Integer.valueOf(s[6])));
        }
        for (Store store : stores.selectList(new QueryWrapper<Store>().eq("audit_status","APPROVED"))) admin.ensureDefaultSlots(store.getId());
    }
    private void createStore(String login,String phone,String name,String address) {
        User user = auth.createUser(login,phone,"Store@123","STORE","ACTIVE"); Store store = new Store(); store.setUserId(user.getId()); store.setName(name);
        store.setContactName("门店服务顾问"); store.setContactPhone(phone); store.setAddress(address); store.setBusinessHours("周一至周日 09:00–18:00"); store.setAuditStatus("APPROVED"); store.setAuditComment("本地演示门店"); stores.insert(store);
    }
}
