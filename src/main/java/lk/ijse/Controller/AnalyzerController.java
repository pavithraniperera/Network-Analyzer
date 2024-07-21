package lk.ijse.Controller;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.element.Image;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.itextpdf.io.font.cmap.CMapContentParser.toHex;

public class AnalyzerController {

    @FXML
    private TextField hostField;

    @FXML
    private TextArea resultArea;

    @FXML
    private TextArea recommendationArea;

    @FXML
    private LineChart<Number, Number> latencyChart;

    private XYChart.Series<Number, Number> series;
    private XYSeries jFreeSeries;
    private int timeCounter = 0;
    private AtomicBoolean running = new AtomicBoolean(false);
    private List<Double> latencyData = new ArrayList<>();



    @FXML
    public void initialize() {
        series = new XYChart.Series<>();
        latencyChart.getData().add(series);
        jFreeSeries = new XYSeries("Latency");
    }

    @FXML
    private void analyzeLatency() {
        String host = hostField.getText();
        if (host.isEmpty()) {
            resultArea.setText("Please enter at least one host.");
            return;
        }
      /*  String[] hosts = hostsInput.split(",");
        for (String host : hosts) {
            host = host.trim();
            if (!host.isEmpty()) {
                analyzeSingleHost(host);
            }
        }*/


        running.set(true);
        new Thread(() -> {
            while (running.get()) {
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    String command;
                    if (os.contains("win")) {
                        command = "ping -n 1 " + host; // Windows
                    } else {
                        command = "ping -4 -c 1  " + host; // Unix/Linux/MacOS
                    }

                    Process process = Runtime.getRuntime().exec(command);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    StringBuilder output = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        System.out.println("DEBUG: " + line); // Debug print for each line of output
                        output.append(line).append("\n");
                        Matcher matcher = Pattern.compile("time=(\\d+\\.\\d*) ms").matcher(line);
                        if (matcher.find()) {
                            double latency = Double.parseDouble(matcher.group(1));
                            Platform.runLater(() -> updateChart(latency));
                        }
                    }

                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        Platform.runLater(() -> resultArea.setText(output.toString()));
                    } else {
                        Platform.runLater(() -> resultArea.setText("Error executing ping command."));
                    }

                    Thread.sleep(1000);  // Wait 1 second before next ping
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> resultArea.setText("An error occurred: " + e.getMessage()));
                }
            }
        }).start();
    }






    @FXML
    private void stopAnalysis() {
        running.set(false);
    }

    private void updateChart(double latency) {

        series.getData().add(new XYChart.Data<>(timeCounter, latency));
        jFreeSeries.add(timeCounter, latency);
        latencyData.add(latency);
        timeCounter++;
        if (series.getData().size() > 20) {
            series.getData().remove(0);
        }

        String recommendations = analyzeLatencyData();
        recommendationArea.setText(recommendations);
    }

    private String analyzeLatencyData() {
        if (latencyData.isEmpty()) {
            return "No latency data available for analysis.";
        }

        double maxLatency = latencyData.stream().mapToDouble(v -> v).max().orElse(0.0);
        double averageLatency = latencyData.stream().mapToDouble(v -> v).average().orElse(0.0);

        StringBuilder recommendations = new StringBuilder();

        recommendations.append(String.format("Maximum Latency: %.2f ms\n", maxLatency));
        recommendations.append(String.format("Average Latency: %.2f ms\n", averageLatency));

        if (averageLatency < 100) {
            recommendations.append("Latency is good. Your network performance is excellent.\n");
        } else if (averageLatency < 200) {
            recommendations.append("Latency is acceptable. Your network performance is decent but could be improved.\n");
        } else {
            recommendations.append("Latency is poor. Consider checking your network connection or contacting your ISP.\n");
        }

        return recommendations.toString();
    }

    @FXML
    private void generateReport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try {
                createPdfReport(file);
            } catch (IOException e) {
                e.printStackTrace();
                showAlert("Error", "Could not save report: " + e.getMessage());
            }
        }
    }

    private void createPdfReport(File file) throws IOException {
        String dest = file.getAbsolutePath();
        PdfWriter writer = new PdfWriter(dest);
        com.itextpdf.kernel.pdf.PdfDocument pdf = new com.itextpdf.kernel.pdf.PdfDocument(writer);
        Document document = new Document(pdf);

        // Load fonts
        PdfFont titleFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont textFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        // Add Title
        document.add(new Paragraph("Network Latency Report")
                .setTextAlignment(TextAlignment.CENTER)
                .setFont(titleFont)
                .setFontSize(20)
                .setBold()
                .setFontColor(ColorConstants.BLUE));

        // Add some space
        document.add(new Paragraph("\n"));

        // Add Host Information
        document.add(new Paragraph("Host: " + hostField.getText())
                .setFont(textFont)
                .setFontSize(12)
                .setFontColor(ColorConstants.BLACK));

        // Add some space
        document.add(new Paragraph("\n"));

        // Add Results
        document.add(new Paragraph("Results:")
                .setFont(titleFont)
                .setFontSize(14)
                .setBold()
                .setFontColor(ColorConstants.DARK_GRAY));

        String[] results = resultArea.getText().split("\n");
        for (String result : results) {
            document.add(new Paragraph(result)
                    .setFont(textFont)
                    .setFontSize(12)
                    .setFontColor(ColorConstants.BLACK));
        }

        // Add some space
        document.add(new Paragraph("\n"));

        // Generate chart and add to PDF
        BufferedImage chartImage = generateChartImage();
        ImageData imageData = ImageDataFactory.create(chartImage, null);
        Image pdfImage = new Image(imageData);
        document.add(pdfImage);

        // Add some space
        document.add(new Paragraph("\n"));

        // Add Recommendations
        document.add(new Paragraph("Recommendations:")
                .setFont(titleFont)
                .setFontSize(14)
                .setBold()
                .setFontColor(ColorConstants.DARK_GRAY));
        String recommendations = analyzeLatencyData();
        document.add(new Paragraph(recommendations)
                .setFont(textFont)
                .setFontSize(12)
                .setFontColor(ColorConstants.BLACK));

        // Close the document
        document.close();

        showAlert("Success", "Report saved successfully.");
    }



    private BufferedImage generateChartImage() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(jFreeSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Latency Over Time",
                "Time",
                "Latency (ms)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, Color.BLUE);
        plot.setRenderer(renderer);

        BufferedImage image = chart.createBufferedImage(600, 400);
        return image;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void clearFields(ActionEvent actionEvent) {
        hostField.clear();
        resultArea.clear();
        recommendationArea.clear();
        series.getData().clear();
        jFreeSeries.clear();
        latencyData.clear();
        timeCounter = 0;
        // Clear the chart data
        latencyChart.getData().clear();

        // Re-add the empty series to the chart
        series = new XYChart.Series<>();
        latencyChart.getData().add(series);

    }
}
