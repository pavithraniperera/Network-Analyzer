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
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzerController {

    @FXML
    private TextField hostsField;

    @FXML
    private TextArea resultArea;

    @FXML
    private TextArea recommendationArea;

    @FXML
    private LineChart<Number, Number> latencyChart;

    private Map<String, XYChart.Series<Number, Number>> seriesMap;
    private Map<String, XYSeries> jFreeSeriesMap;
    private Map<String, List<Double>> latencyMap;
    private AtomicBoolean running = new AtomicBoolean(false);

    @FXML
    public void initialize() {
        seriesMap = new HashMap<>();
        jFreeSeriesMap = new HashMap<>();
        latencyMap = new HashMap<>();
    }

    @FXML
    private void analyzeLatency() {
        String hostsInput = hostsField.getText();
        if (hostsInput.isEmpty()) {
            resultArea.setText("Please enter at least one host.");
            return;
        }

        String[] hosts = hostsInput.split(",");
        latencyMap.clear();
        seriesMap.clear();
        jFreeSeriesMap.clear();
        latencyChart.getData().clear();
        recommendationArea.clear(); // Clear recommendations initially

        for (String host : hosts) {
            host = host.trim();
            if (!host.isEmpty()) {
                latencyMap.put(host, new ArrayList<>());
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName(host);
                seriesMap.put(host, series);
                latencyChart.getData().add(series);
                jFreeSeriesMap.put(host, new XYSeries(host));
            }
        }

        running.set(true);
        new Thread(() -> {
            while (running.get()) {
                for (String host : hosts) {
                    host = host.trim();
                    if (host.isEmpty()) continue;

                    try {
                        String os = System.getProperty("os.name").toLowerCase();
                        String command;
                        if (os.contains("win")) {
                            command = "ping -n 1 " + host; // Windows
                        } else {
                            command = "ping -4 -c 1 " + host; // Unix/Linux/MacOS
                        }

                        Process process = Runtime.getRuntime().exec(command);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        StringBuilder output = new StringBuilder();

                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                            Matcher matcher = Pattern.compile("time=(\\d+\\.\\d*) ms").matcher(line);
                            if (matcher.find()) {
                                double latency = Double.parseDouble(matcher.group(1));
                                latencyMap.get(host).add(latency);
                                String finalHost = host;
                                Platform.runLater(() -> updateChart(latency, finalHost));
                            }
                        }

                        int exitCode = process.waitFor();
                        if (exitCode == 0) {
                            Platform.runLater(() -> resultArea.setText(output.toString()));
                        } else {
                            String finalHost1 = host;
                            Platform.runLater(() -> resultArea.setText("Error executing ping command for host: " + finalHost1));
                        }

                        Thread.sleep(1000);  // Wait 1 second before next ping
                    } catch (Exception e) {
                        e.printStackTrace();
                        String finalHost2 = host;
                        Platform.runLater(() -> resultArea.setText("An error occurred for host " + finalHost2 + ": " + e.getMessage()));
                    }
                }
            }
        }).start();
    }

    @FXML
    private void stopAnalysis() {
        running.set(false);
    }

    private void updateChart(double latency, String host) {
        XYChart.Series<Number, Number> series = seriesMap.get(host);
        XYSeries jFreeSeries = jFreeSeriesMap.get(host);
        int timeCounter = series.getData().size();

        series.getData().add(new XYChart.Data<>(timeCounter, latency));
        jFreeSeries.add(timeCounter, latency);
        if (series.getData().size() > 20) {
            series.getData().remove(0);
        }

        // Update recommendations for all hosts
        String recommendations = analyzeLatencyData();
        recommendationArea.setText(recommendations);
    }

    private String analyzeLatencyData() {
        StringBuilder recommendations = new StringBuilder();
        for (Map.Entry<String, List<Double>> entry : latencyMap.entrySet()) {
            String host = entry.getKey();
            List<Double> latencyData = entry.getValue();

            if (latencyData.isEmpty()) {
                recommendations.append("Host: ").append(host).append("\nNo latency data available for analysis.\n\n");
                continue;
            }

            double maxLatency = latencyData.stream().mapToDouble(v -> v).max().orElse(0.0);
            double averageLatency = latencyData.stream().mapToDouble(v -> v).average().orElse(0.0);

            recommendations.append("Host: ").append(host).append("\n");
            recommendations.append(String.format("Maximum Latency: %.2f ms\n", maxLatency));
            recommendations.append(String.format("Average Latency: %.2f ms\n", averageLatency));

            if (averageLatency < 100) {
                recommendations.append("Latency is good. Your network performance is excellent.\n");
            } else if (averageLatency < 200) {
                recommendations.append("Latency is acceptable. Your network performance is decent but could be improved.\n");
            } else {
                recommendations.append("Latency is poor. Consider checking your network connection or contacting your ISP.\n");
            }

            recommendations.append("\n");
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

        PdfFont titleFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont textFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        document.add(new Paragraph("Network Latency Report")
                .setTextAlignment(TextAlignment.CENTER)
                .setFont(titleFont)
                .setFontSize(20)
                .setBold()
                .setFontColor(ColorConstants.BLUE));
        document.add(new Paragraph("\n"));

        document.add(new Paragraph("Hosts: " + hostsField.getText())
                .setFont(textFont)
                .setFontSize(12)
                .setFontColor(ColorConstants.BLACK));
        document.add(new Paragraph("\n"));

        document.add(new Paragraph("Results:")
                .setFont(titleFont)
                .setFontSize(14)
                .setBold()
                .setFontColor(ColorConstants.DARK_GRAY));

        for (String host : hostsField.getText().split(",")) {
            host = host.trim();
            if (host.isEmpty()) continue;

            document.add(new Paragraph("Host: " + host)
                    .setFont(textFont)
                    .setFontSize(12)
                    .setFontColor(ColorConstants.BLACK));

            List<Double> hostLatencyData = latencyMap.get(host);
            if (hostLatencyData != null && !hostLatencyData.isEmpty()) {
                StringBuilder hostResults = new StringBuilder();
                for (Double latency : hostLatencyData) {
                    hostResults.append(String.format("Latency: %.2f ms\n", latency));
                }
                document.add(new Paragraph(hostResults.toString())
                        .setFont(textFont)
                        .setFontSize(12)
                        .setFontColor(ColorConstants.BLACK));

                document.add(new Paragraph("\n"));

                String hostRecommendations = analyzeLatencyDataForHost(host);
                document.add(new Paragraph("Recommendations:")
                        .setFont(titleFont)
                        .setFontSize(14)
                        .setBold()
                        .setFontColor(ColorConstants.DARK_GRAY));
                document.add(new Paragraph(hostRecommendations)
                        .setFont(textFont)
                        .setFontSize(12)
                        .setFontColor(ColorConstants.BLACK));
                document.add(new Paragraph("\n"));
            }
        }

        BufferedImage chartImage = generateChartImage();
        ImageData imageData = ImageDataFactory.create(chartImage, null);
        Image pdfImage = new Image(imageData);
        document.add(pdfImage);
        document.close();
        showAlert("Success", "Report saved successfully.");
    }

    private String analyzeLatencyDataForHost(String host) {
        List<Double> latencyData = latencyMap.get(host);
        if (latencyData == null || latencyData.isEmpty()) {
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

    private BufferedImage generateChartImage() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (XYSeries series : jFreeSeriesMap.values()) {
            dataset.addSeries(series);
        }

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
        plot.setRenderer(renderer);
        plot.setBackgroundPaint(Color.white);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.BLACK);
        plot.setDomainGridlinesVisible(true);
        plot.setDomainGridlinePaint(Color.BLACK);

        BufferedImage image = chart.createBufferedImage(600, 400);
        return image;
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void clearFields(ActionEvent actionEvent) {
        hostsField.clear();
        resultArea.clear();
        recommendationArea.clear();
        seriesMap.clear();
        jFreeSeriesMap.clear();
        latencyMap.clear();
        latencyChart.getData().clear();
    }
}
