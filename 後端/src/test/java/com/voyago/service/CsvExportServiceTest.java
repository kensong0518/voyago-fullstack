package com.voyago.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvExportServiceTest {

    @Test
    void cell_escapesQuotesCommasNewlines_andFormulaPrefixes() {
        assertEquals("", CsvExportService.cell(null));
        assertEquals("王小明", CsvExportService.cell("王小明"));
        assertEquals("\"a,b\"", CsvExportService.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExportService.cell("say \"hi\""));
        assertEquals("\"a\nb\"", CsvExportService.cell("a\nb"));
        assertEquals("'=1+1", CsvExportService.cell("=1+1"));
        assertEquals("'@SUM(A1)", CsvExportService.cell("@SUM(A1)"));
        assertEquals("'+886912", CsvExportService.cell("+886912"));
        assertEquals("-3000", CsvExportService.cell(-3000));   // 數字本身不加
    }
}
