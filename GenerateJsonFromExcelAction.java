package com.dbs.plugin.action;

import com.dbs.plugin.model.ApiMapping;
import com.dbs.plugin.service.MainframeRequestJsonService;
import com.dbs.plugin.service.MainframeResponseJsonService;
import com.dbs.plugin.service.SunCbsRequestJsonService;
import com.dbs.plugin.service.SunCbsResponseJsonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.io.File;
import java.util.Map;

public class GenerateJsonFromExcelAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("No open project found.", "Error");
            return;
        }

        // Excel file chooser
        FileChooserDescriptor excelDescriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        PathChooserDialog excelDialog = FileChooserFactory.getInstance().createPathChooser(excelDescriptor, project, null);

        excelDialog.choose(null, virtualFiles -> {
            if (virtualFiles.size() != 1) {
                Messages.showErrorDialog("Please select exactly one Excel file.", "Error");
                return;
            }

            File excelFile = new File(virtualFiles.get(0).getPath());

            // Output directory chooser
            FileChooserDescriptor dirDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
            PathChooserDialog outputDialog = FileChooserFactory.getInstance().createPathChooser(dirDescriptor, project, null);

            outputDialog.choose(null, outputFiles -> {
                if (outputFiles.size() != 1) {
                    Messages.showErrorDialog("Please select exactly one output folder.", "Error");
                    return;
                }

                File outputDir = new File(outputFiles.get(0).getPath());
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);

                    // ✅ Sun CBS Request
                    SunCbsRequestJsonService sunCbsReqService = new SunCbsRequestJsonService();
                    Map<String, ApiMapping> sunCbsReq = sunCbsReqService.extractSunCbsRequestMappings(excelFile);
                    for (Map.Entry<String, ApiMapping> entry : sunCbsReq.entrySet()) {
                        mapper.writeValue(new File(outputDir, entry.getKey()), entry.getValue());
                    }

                    // ✅ Mainframe Request
                    MainframeRequestJsonService mainframeReqService = new MainframeRequestJsonService();
                    Map<String, ApiMapping> mainframeReq = mainframeReqService.extractMainframeRequestMappings(excelFile);
                    for (Map.Entry<String, ApiMapping> entry : mainframeReq.entrySet()) {
                        mapper.writeValue(new File(outputDir, entry.getKey()), entry.getValue());
                    }

                    // ✅ Sun CBS Response
                    SunCbsResponseJsonService sunCbsRespService = new SunCbsResponseJsonService();
                    Map<String, ApiMapping> sunCbsResp = sunCbsRespService.extractSunCbsResponseMappings(excelFile);
                    for (Map.Entry<String, ApiMapping> entry : sunCbsResp.entrySet()) {
                        mapper.writeValue(new File(outputDir, entry.getKey()), entry.getValue());
                    }

                    // ✅ Mainframe Response
                    MainframeResponseJsonService mainframeRespService = new MainframeResponseJsonService();
                    Map<String, ApiMapping> mainframeResp = mainframeRespService.extractMainframeResponseMappings(excelFile);
                    for (Map.Entry<String, ApiMapping> entry : mainframeResp.entrySet()) {
                        mapper.writeValue(new File(outputDir, entry.getKey()), entry.getValue());
                    }

                    Messages.showInfoMessage("✅ JSONs generated successfully!\nSaved in: " + outputDir.getPath(), "Success");

                } catch (Exception ex) {
                    ex.printStackTrace();
                    Messages.showErrorDialog("❌ Failed to generate JSONs: " + ex.getMessage(), "Error");
                }
            });
        });
    }
}