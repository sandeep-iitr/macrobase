package edu.stanford.futuredata.macrobase.ingest;

import com.univocity.parsers.csv.CsvParserSettings;
import edu.stanford.futuredata.macrobase.datamodel.DataFrame;
import edu.stanford.futuredata.macrobase.datamodel.Row;
import edu.stanford.futuredata.macrobase.datamodel.Schema;
import com.univocity.parsers.csv.CsvParser;

import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Arrays;

public class CSVDataFrameParser implements DataFrameLoader {
    private CsvParser parser;
    private final List<String> requiredColumns;
    private Map<String, Schema.ColType> columnTypes;

    public CSVDataFrameParser(CsvParser parser, List<String> requiredColumns) {
        this.requiredColumns = requiredColumns;
        this.parser = parser;
    }

    public CSVDataFrameParser(String fileName, List<String> requiredColumns) throws IOException {
        this.requiredColumns = requiredColumns;
        CsvParserSettings settings = new CsvParserSettings();
        settings.getFormat().setLineSeparator("\n");
        CsvParser csvParser = new CsvParser(settings);
        csvParser.beginParsing(getReader(fileName));
        this.parser = csvParser;
    }

    @Override
    public DataFrameLoader setColumnTypes(Map<String, Schema.ColType> types) {
        this.columnTypes = types;
        return this;
    }

    @Override
    public DataFrame load() throws Exception {
        String[] header = parser.parseNext();

        int numColumns = header.length;
        int schemaLength = requiredColumns.size();
        int schemaIndexMap[] = new int[numColumns];
        Arrays.fill(schemaIndexMap, -1);

        String[] columnNameList = new String[schemaLength];
        Schema.ColType[] columnTypeList = new Schema.ColType[schemaLength];
        for (int c = 0, schemaIndex = 0; c < numColumns; c++) {
            String columnName = header[c];
            Schema.ColType t = columnTypes.getOrDefault(columnName, Schema.ColType.STRING);
            if (requiredColumns.contains(columnName)) {
                columnNameList[schemaIndex] = columnName;
                columnTypeList[schemaIndex] = t;
                schemaIndexMap[c] = schemaIndex;
                schemaIndex++;
            }
        }
        // Make sure to generate the schema in the right order
        Schema schema = new Schema();
        int numStringColumns = 0;
        int numDoubleColumns = 0;
        for (int c = 0; c < schemaLength; c++) {
            schema.addColumn(columnTypeList[c], columnNameList[c]);
            if (columnTypeList[c] == Schema.ColType.STRING) {
                numStringColumns++;
            } else if (columnTypeList[c] == Schema.ColType.DOUBLE) {
                numDoubleColumns++;
            } else {
                throw new RuntimeException("Bad ColType");
            }
        }

        ArrayList<String>[] stringColumns = (ArrayList<String>[])new ArrayList[numStringColumns];
        for (int i = 0; i < numStringColumns; i++) {
            stringColumns[i] = new ArrayList<>();
        }
        ArrayList<Double>[] doubleColumns = (ArrayList<Double>[])new ArrayList[numDoubleColumns];
        for (int i = 0; i < numDoubleColumns; i++) {
            doubleColumns[i] = new ArrayList<>();
        }

        String[] row;
        while ((row = parser.parseNext()) != null) {
            for (int c = 0, stringColNum = 0, doubleColNum = 0; c < numColumns; c++) {
                if (schemaIndexMap[c] >= 0) {
                    int schemaIndex = schemaIndexMap[c];
                    Schema.ColType t = columnTypeList[schemaIndex];
                    String rowValue = row[c];
                    if (t == Schema.ColType.STRING) {
                        stringColumns[stringColNum++].add(rowValue);
                    } else if (t == Schema.ColType.DOUBLE) {
                        try {
                            doubleColumns[doubleColNum++].add(Double.parseDouble(rowValue));
                        } catch (NumberFormatException e) {
                            doubleColumns[doubleColNum++].add(Double.NaN);
                        }
                    } else {
                        throw new RuntimeException("Bad ColType");
                    }
                }
            }
        }

        DataFrame df = new DataFrame(schema, stringColumns, doubleColumns);
        return df;
    }

    private static Reader getReader(String path) {
        try {
            InputStream targetStream = new FileInputStream(path);
            return new InputStreamReader(targetStream, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unable to read input", e);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Unable to read input", e);
        }
    }
}
