package com.example.identity_service.region;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PostgresRegionProvider implements RegionProvider {
    private final JdbcTemplate jdbc;

    PostgresRegionProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Province> provinces() {
        try {
            return jdbc.query("select code, name from region_provinces order by code",
                    (row, index) -> new Province(row.getString(1), row.getString(2)));
        } catch (DataAccessException unavailable) {
            throw new ApiException(ErrorCode.REGION_SOURCE_UNAVAILABLE);
        }
    }

    @Override
    public List<Ward> wards(String provinceCode) {
        try {
            return jdbc.query("select code, name, province_code from region_wards where province_code = ? order by code",
                    (row, index) -> new Ward(row.getString(1), row.getString(2), row.getString(3)), provinceCode);
        } catch (DataAccessException unavailable) {
            throw new ApiException(ErrorCode.REGION_SOURCE_UNAVAILABLE);
        }
    }
}
