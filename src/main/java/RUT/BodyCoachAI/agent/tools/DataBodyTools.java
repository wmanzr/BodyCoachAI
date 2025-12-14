package RUT.BodyCoachAI.agent.tools;

import RUT.BodyCoachAI.model.InBodyData;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class DataBodyTools {
    @Tool("Составляет таблицу статистики с данными из отчета Inbody, который скинул пользователь")
    public String createTableByDataForUser(InBodyData data) {
        String fileName = "inbody_data_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                ".xlsx";

        Path dataDir = Path.of("data");
        if (!dataDir.toFile().exists()) {
            dataDir.toFile().mkdirs();
        }
        
        Path filePath = dataDir.resolve(fileName);

        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            Sheet sheet = workbook.createSheet("InBody Data");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Параметр");
            headerRow.createCell(1).setCellValue("Значение");

            int rowIndex = 1;
            rowIndex = addDataRow(sheet, rowIndex, "Возраст (лет)", data.getAge());
            rowIndex = addDataRow(sheet, rowIndex, "Рост (см)", data.getHeight());
            rowIndex = addDataRow(sheet, rowIndex, "Пол", data.getGender());
            rowIndex = addDataRow(sheet, rowIndex, "Вес (кг)", data.getWeight());
            rowIndex = addDataRow(sheet, rowIndex, "Мышечная масса (кг)", data.getMuscleMass());
            rowIndex = addDataRow(sheet, rowIndex, "Масса жира (кг)", data.getFatMass());
            rowIndex = addDataRow(sheet, rowIndex, "Процент жира (%)", data.getBodyFatPercentage());
            rowIndex = addDataRow(sheet, rowIndex, "ИМТ (BMI)", data.getBmi());
            rowIndex = addDataRow(sheet, rowIndex, "Висцеральный жир (уровень)", data.getVisceralFatLevel());
            rowIndex = addDataRow(sheet, rowIndex, "BMR (ккал)", data.getBmr());
            rowIndex = addDataRow(sheet, rowIndex, "InBody Score", data.getInBodyScore());

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);

            workbook.write(fos);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при создании Excel-файла", e);
        }
        return fileName;
    }

    private int addDataRow(Sheet sheet, int rowIndex, String param, Object value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(param);
        row.createCell(1).setCellValue(value != null ? String.valueOf(value) : "N/A");
        return rowIndex + 1;
    }

    @Tool("Рассчитывает пользователю КБЖУ (калории, белки, жиры, углеводы), дает рекомендацию в цифрах")
    public Map<String, Double> calculateBodyStats(InBodyData data) {
        Map<String, Double> stats = new HashMap<>();
        
        if (data.getBmr() != null) {
            double bmr = data.getBmr();
            double protein = bmr * 0.3 / 4;
            double fat = bmr * 0.3 / 9;
            double carbs = bmr * 0.4 / 4;
            
            stats.put("calories", bmr * 1.2);
            stats.put("protein", protein);
            stats.put("fat", fat);
            stats.put("carbs", carbs);
        }
        return stats;
    }
}