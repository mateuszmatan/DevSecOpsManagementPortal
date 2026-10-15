package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.settings.Scanner;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Converter(autoApply = true)
public class ScannerListConverter implements AttributeConverter<List<Scanner>, String> {

    @Override
    public String convertToDatabaseColumn(List<Scanner> scanners) {
        return isEmpty(scanners) ? null
                : String.join(",", scanners.stream().map(Scanner::name).toList());
    }

    @Override
    public List<Scanner> convertToEntityAttribute(String column) {
        return isBlank(column) ? List.of()
                : Arrays.stream(column.split(",")).map(String::trim).map(Scanner::valueOf).toList();
    }
}
