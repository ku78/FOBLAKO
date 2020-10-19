package ru.zvyagin.vitalya.foblako.client;


import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ru.zvyagin.vitalya.common.FileInfo;

import java.nio.file.Path;
import java.util.List;

public class GUIHelper {
    public static List<FileInfo> serverFilesList;
    public static Path currentClientPath;
    public static Path currentServerPath;
    public static String targetServerDirectory;



    public static void updateUI(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    public static void setFileLabel(TableView<FileInfo> filesTable, Label fileLabel) {
        TableView.TableViewSelectionModel<FileInfo> selectionModel = filesTable.getSelectionModel();
        selectionModel.selectedItemProperty().addListener(new ChangeListener<FileInfo>() {
            @Override
            public void changed(ObservableValue<? extends FileInfo> observableValue, FileInfo oldInfo, FileInfo newInfo) {
                if (newInfo != null) {
                    fileLabel.setText(newInfo.getFileName());
                }
            }
        });
    }

    public static void setCellValue(TableView<FileInfo> filesTable) {
        TableColumn<FileInfo, String> fileTypeColumn = new TableColumn<>();
        fileTypeColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getType().getName()));
        fileTypeColumn.setPrefWidth(30);

        TableColumn<FileInfo, String> filenameColumn = new TableColumn<>("Имя файла");
        filenameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getFileName()));
        filenameColumn.setPrefWidth(240);

        TableColumn<FileInfo, Long> fileSizeColumn = new TableColumn<>("Размер");
        fileSizeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getSize()));
        fileSizeColumn.setCellFactory(column -> new TableCell<FileInfo, Long>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    String text = String.format("%,d bytes", item);
                    if (item == -1L) {
                        text = "[DIR]";
                    }
                    setText(text);
                }
            }
        });

        fileSizeColumn.setPrefWidth(120);
        updateUI(() -> {
            filesTable.getColumns().addAll(fileTypeColumn, filenameColumn, fileSizeColumn);
            filesTable.getSortOrder().add(fileTypeColumn);
        });


    }


    public static void showError(Exception e) {
        GUIHelper.updateUI(() -> {

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Something went wrong!");
            alert.setHeaderText(e.getMessage());

            VBox dialogPaneContent = new VBox();
            Label label = new Label("Stack Trace:");

            String stackTrace = ExceptionUtils.getStackTrace(e);
            TextArea textArea = new TextArea();
            textArea.setText(stackTrace);

            dialogPaneContent.getChildren().addAll(label, textArea);

            // Set content for Dialog Pane
            alert.getDialogPane().setContent(dialogPaneContent);
            alert.setResizable(true);
            alert.showAndWait();

            e.printStackTrace();
        });
    }

}

