package br.edu.evalia.service;

import br.edu.evalia.model.PlayerDiagnosis;
import br.edu.evalia.model.RepertoireHole;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ReportPdfService {
    public void export(Path target, PlayerDiagnosis diagnosis, List<RepertoireHole> holes,
                       List<String> trainingPlan) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("Evalia - Relatorio de Diagnostico");
        lines.add("Partidas analisadas: " + diagnosis.totalGames());
        lines.add("Aproveitamento geral: " + "%.1f%%".formatted(diagnosis.scoreRate()));
        lines.add("Forca principal: " + diagnosis.mainStrength());
        lines.add("Maior fraqueza: " + diagnosis.mainWeakness());
        lines.add("Melhor abertura: " + diagnosis.bestOpening());
        lines.add("Pior abertura: " + diagnosis.worstOpening());
        lines.add("Fase critica: " + diagnosis.criticalPhase());
        lines.add("Brancas: " + "%.1f%%".formatted(diagnosis.whiteScoreRate())
                + " | Pretas: " + "%.1f%%".formatted(diagnosis.blackScoreRate()));
        lines.add("");
        lines.add("Buracos de repertorio");
        if (holes.isEmpty()) lines.add("- Nenhum buraco forte detectado.");
        for (RepertoireHole hole : holes) {
            lines.add("- " + hole.opening() + ": " + "%.1f%%".formatted(hole.scoreRate())
                    + " em " + hole.games() + " partidas.");
        }
        lines.add("");
        lines.add("Plano de treino");
        for (String item : trainingPlan) lines.add("- " + item);
        writePdf(target, lines);
    }

    private void writePdf(Path target, List<String> lines) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 13 Tf\n50 790 Td\n");
        int emitted = 0;
        for (String line : lines) {
            if (emitted > 0) stream.append("0 -18 Td\n");
            stream.append("(").append(escape(line)).append(") Tj\n");
            emitted++;
            if (emitted >= 40) break;
        }
        stream.append("ET\n");
        String content = stream.toString();

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>\n",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\n",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\n",
                "<< /Length " + content.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + content + "endstream\n"
        );
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("endobj\n");
        }
        int xref = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (int offset : offsets) pdf.append("%010d 00000 n \n".formatted(offset));
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xref).append("\n%%EOF");
        Files.writeString(target, pdf.toString(), StandardCharsets.ISO_8859_1);
    }

    private String escape(String line) {
        return line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }
}
