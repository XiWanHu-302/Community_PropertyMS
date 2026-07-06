package com.community.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.Result;
import com.community.user.entity.Building;
import com.community.user.entity.Household;
import com.community.user.mapper.HouseholdMapper;
import com.community.user.service.BuildingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Comparator;
import java.util.List;

/**
 * 楼栋管理控制器 —— 仅管理员可操作
 */
@RestController
@RequestMapping("/building")
public class BuildingController {

    @Resource
    private BuildingService buildingService;

    @Resource
    private HouseholdMapper householdMapper;

    /**
     * 查询全部楼栋
     * GET /building/list
     */
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Building>> list() {
        List<Building> list = buildingService.list();
        // 按楼号数值排序（VARCHAR 直接排序是字典序：1,10,2 → 转为数值：1,2,10）
        list.sort(Comparator.comparingInt(b -> Integer.parseInt(b.getBuildingNo())));
        return Result.ok(list);
    }

    /**
     * 根据楼号查询
     * GET /building/{buildingNo}
     */
    @GetMapping("/{buildingNo}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Building> getById(@PathVariable String buildingNo) {
        Building building = buildingService.getById(buildingNo);
        if (building == null) {
            return Result.fail("楼栋不存在");
        }
        return Result.ok(building);
    }

    /**
     * 新增楼栋
     * POST /building
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> add(@Validated @RequestBody Building building) {
        // 检查楼号是否已存在
        Building exist = buildingService.getById(building.getBuildingNo());
        if (exist != null) {
            return Result.fail("楼号 " + building.getBuildingNo() + " 已存在");
        }
        buildingService.save(building);
        return Result.ok("楼栋添加成功");
    }

    /**
     * 修改楼栋
     * PUT /building
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@Validated @RequestBody Building building) {
        Building exist = buildingService.getById(building.getBuildingNo());
        if (exist == null) {
            return Result.fail("楼栋不存在");
        }
        buildingService.updateById(building);
        return Result.ok("楼栋修改成功");
    }

    /**
     * 删除楼栋
     * DELETE /building/{buildingNo}
     * 删除前检查是否有住户关联
     */
    @DeleteMapping("/{buildingNo}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable String buildingNo) {
        // 检查是否有住户属于这栋楼
        Long count = householdMapper.selectCount(
                new LambdaQueryWrapper<Household>()
                        .eq(Household::getBuildingNo, buildingNo)
        );
        if (count > 0) {
            return Result.fail("该楼栋下还有 " + count + " 个住户，不能删除");
        }
        buildingService.removeById(buildingNo);
        return Result.ok("楼栋删除成功");
    }
}
