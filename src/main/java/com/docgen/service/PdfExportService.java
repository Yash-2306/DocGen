package com.docgen.service;

import com.docgen.model.ApiDocumentation;
import com.docgen.model.ApiDocumentation.Endpoint;
import com.docgen.model.ApiDocumentation.Parameter;
import com.docgen.model.ApiDocumentation.ResponseInfo;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class PdfExportService {

    // Style colors
    private static final Color COLOR_PRIMARY = new Color(15, 23, 42); // Slate 900
    private static final Color COLOR_SECONDARY = new Color(71, 85, 105); // Slate 600
    private static final Color COLOR_BG_LIGHT = new Color(248, 250, 252); // Slate 50
    private static final Color COLOR_BORDER = new Color(226, 232, 240); // Slate 200

    // HTTP Method Colors
    private static final Color COLOR_GET = new Color(13, 148, 136); // Teal
    private static final Color COLOR_POST = new Color(22, 163, 74); // Green
    private static final Color COLOR_PUT = new Color(217, 119, 6); // Amber
    private static final Color COLOR_DELETE = new Color(220, 38, 38); // Red

    public byte[] generatePdf(ApiDocumentation docs) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 54, 54);
        PdfWriter.getInstance(document, out);

        document.open();

        // 1. Fonts Setup
        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, COLOR_PRIMARY);
        Font fontSubTitle = FontFactory.getFont(FontFactory.HELVETICA, 12, COLOR_SECONDARY);
        Font fontHeading2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_PRIMARY);
        Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_PRIMARY);
        Font fontBodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARY);
        Font fontCode = FontFactory.getFont(FontFactory.COURIER, 9, COLOR_PRIMARY);
        Font fontTableHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

        // 2. Title & Description
        Paragraph title = new Paragraph(docs.getTitle(), fontTitle);
        title.setSpacingAfter(8);
        document.add(title);

        Paragraph desc = new Paragraph(docs.getDescription(), fontSubTitle);
        desc.setSpacingAfter(15);
        document.add(desc);

        if (docs.getBasePath() != null && !docs.getBasePath().isEmpty()) {
            Paragraph basePath = new Paragraph("Base Path: " + docs.getBasePath(), fontBodyBold);
            basePath.setSpacingAfter(20);
            document.add(basePath);
        }

        // Horizontal Rule
        Paragraph hr = new Paragraph("______________________________________________________________________________", fontSubTitle);
        hr.setSpacingAfter(20);
        document.add(hr);

        // 3. Endpoints Listing
        List<Endpoint> endpoints = docs.getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            document.add(new Paragraph("No endpoints found.", fontBody));
        } else {
            for (Endpoint endpoint : endpoints) {
                // Method + Path Banner
                PdfPTable headerTable = new PdfPTable(2);
                headerTable.setWidthPercentage(100);
                headerTable.setWidths(new float[]{1.5f, 8.5f});
                headerTable.setSpacingBefore(10);
                headerTable.setSpacingAfter(8);

                // Method Cell
                String method = endpoint.getMethod().toUpperCase();
                Color methodBg = getMethodColor(method);
                PdfPCell methodCell = new PdfPCell(new Phrase(method, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE)));
                methodCell.setBackgroundColor(methodBg);
                methodCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                methodCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                methodCell.setPadding(6);
                methodCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
                headerTable.addCell(methodCell);

                // Path Cell
                PdfPCell pathCell = new PdfPCell(new Phrase("  " + endpoint.getPath(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_PRIMARY)));
                pathCell.setBackgroundColor(COLOR_BG_LIGHT);
                pathCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                pathCell.setPadding(6);
                pathCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
                headerTable.addCell(pathCell);

                document.add(headerTable);

                // Summary and Description
                if (endpoint.getSummary() != null && !endpoint.getSummary().isEmpty()) {
                    Paragraph summary = new Paragraph(endpoint.getSummary(), fontBodyBold);
                    summary.setSpacingAfter(4);
                    document.add(summary);
                }

                if (endpoint.getDescription() != null && !endpoint.getDescription().isEmpty()) {
                    Paragraph endpointDesc = new Paragraph(endpoint.getDescription(), fontBody);
                    endpointDesc.setSpacingAfter(10);
                    document.add(endpointDesc);
                }

                // Headers
                addParametersSection(document, "Headers", endpoint.getHeaders(), fontHeading2, fontTableHeader, fontBody, fontBodyBold);

                // Path Variables
                addParametersSection(document, "Path Variables", endpoint.getPathVariables(), fontHeading2, fontTableHeader, fontBody, fontBodyBold);

                // Query Parameters
                addParametersSection(document, "Query Parameters", endpoint.getQueryParams(), fontHeading2, fontTableHeader, fontBody, fontBodyBold);

                // Request Body
                if (endpoint.getRequestBody() != null && 
                    ((endpoint.getRequestBody().getExampleJson() != null && !endpoint.getRequestBody().getExampleJson().isEmpty()) ||
                     (endpoint.getRequestBody().getDescription() != null && !endpoint.getRequestBody().getDescription().isEmpty()))) {
                    
                    Paragraph reqBodyTitle = new Paragraph("Request Body", fontHeading2);
                    reqBodyTitle.setSpacingBefore(8);
                    reqBodyTitle.setSpacingAfter(4);
                    document.add(reqBodyTitle);

                    if (endpoint.getRequestBody().getDescription() != null && !endpoint.getRequestBody().getDescription().isEmpty()) {
                        Paragraph rbDesc = new Paragraph(endpoint.getRequestBody().getDescription() + " (Content-Type: " + endpoint.getRequestBody().getContentType() + ")", fontBody);
                        rbDesc.setSpacingAfter(5);
                        document.add(rbDesc);
                    }

                    if (endpoint.getRequestBody().getExampleJson() != null && !endpoint.getRequestBody().getExampleJson().isEmpty()) {
                        addCodeBlock(document, endpoint.getRequestBody().getExampleJson(), fontCode);
                    }
                }

                // Responses
                if (endpoint.getResponses() != null && !endpoint.getResponses().isEmpty()) {
                    Paragraph responsesTitle = new Paragraph("Responses", fontHeading2);
                    responsesTitle.setSpacingBefore(8);
                    responsesTitle.setSpacingAfter(4);
                    document.add(responsesTitle);

                    for (ResponseInfo resp : endpoint.getResponses()) {
                        Paragraph respHeader = new Paragraph("Status " + resp.getStatusCode() + " - " + resp.getDescription() + " (" + resp.getContentType() + ")", fontBodyBold);
                        respHeader.setSpacingAfter(4);
                        document.add(respHeader);

                        if (resp.getExampleJson() != null && !resp.getExampleJson().isEmpty()) {
                            addCodeBlock(document, resp.getExampleJson(), fontCode);
                        }
                    }
                }

                // End of Endpoint separator line
                Paragraph sep = new Paragraph(" ", fontBody);
                sep.setSpacingAfter(15);
                document.add(sep);
            }
        }

        document.close();
        return out.toByteArray();
    }

    private Color getMethodColor(String method) {
        switch (method) {
            case "GET": return COLOR_GET;
            case "POST": return COLOR_POST;
            case "PUT": return COLOR_PUT;
            case "DELETE": return COLOR_DELETE;
            default: return COLOR_SECONDARY;
        }
    }

    private void addParametersSection(Document document, String title, List<Parameter> params, 
                                      Font fontHeading, Font fontTableHeader, Font fontBody, Font fontBodyBold) throws Exception {
        if (params == null || params.isEmpty()) {
            return;
        }

        Paragraph sectionTitle = new Paragraph(title, fontHeading);
        sectionTitle.setSpacingBefore(8);
        sectionTitle.setSpacingAfter(4);
        document.add(sectionTitle);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.5f, 2.0f, 1.5f, 4.0f});
        table.setSpacingAfter(10);

        // Headers
        String[] headers = {"Name", "Type", "Required", "Description"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, fontTableHeader));
            cell.setBackgroundColor(COLOR_PRIMARY);
            cell.setPadding(5);
            cell.setBorderColor(COLOR_BORDER);
            table.addCell(cell);
        }

        // Rows
        for (Parameter param : params) {
            PdfPCell nameCell = new PdfPCell(new Phrase(param.getName(), fontBodyBold));
            nameCell.setPadding(5);
            nameCell.setBorderColor(COLOR_BORDER);
            nameCell.setBackgroundColor(COLOR_BG_LIGHT);
            table.addCell(nameCell);

            PdfPCell typeCell = new PdfPCell(new Phrase(param.getType(), fontBody));
            typeCell.setPadding(5);
            typeCell.setBorderColor(COLOR_BORDER);
            table.addCell(typeCell);

            PdfPCell reqCell = new PdfPCell(new Phrase(param.isRequired() ? "Yes" : "No", fontBody));
            reqCell.setPadding(5);
            reqCell.setBorderColor(COLOR_BORDER);
            table.addCell(reqCell);

            PdfPCell descCell = new PdfPCell(new Phrase(param.getDescription(), fontBody));
            descCell.setPadding(5);
            descCell.setBorderColor(COLOR_BORDER);
            table.addCell(descCell);
        }

        document.add(table);
    }

    private void addCodeBlock(Document document, String codeText, Font fontCode) throws Exception {
        PdfPTable container = new PdfPTable(1);
        container.setWidthPercentage(100);
        container.setSpacingAfter(10);

        PdfPCell cell = new PdfPCell(new Phrase(codeText, fontCode));
        cell.setBackgroundColor(COLOR_BG_LIGHT);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(10);
        
        container.addCell(cell);
        document.add(container);
    }
}
