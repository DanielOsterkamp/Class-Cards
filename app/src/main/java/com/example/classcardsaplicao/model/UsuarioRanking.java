package com.example.classcardsaplicao.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public final class UsuarioRanking {
    private final String uid;
    private final String nome;
    private final String fotoUrl;
    private final long xp;
    private final long ofensiva;
    private final long cardsEstudados;
    private final String ultimoDiaEstudo;
    private final String fusoHorario;
    // Bolha Moodle (Fase 9): chaves "<site>:<courseid>" dos cursos do participante (vazio para contas de fora)
    private List<String> cursosChave = Collections.emptyList();

    public UsuarioRanking(String uid, String nome, String fotoUrl, long xp, long ofensiva,
            long cardsEstudados, String ultimoDiaEstudo, String fusoHorario) {
        if (uid == null || uid.trim().isEmpty()) throw new IllegalArgumentException("Usuário inválido.");
        this.uid = uid;
        this.nome = nome == null || nome.trim().isEmpty() ? "Estudante" : nome.trim();
        this.fotoUrl = fotoUrl == null ? "" : fotoUrl.trim();
        this.xp = Math.max(0, xp);
        this.ofensiva = Math.max(0, ofensiva);
        this.cardsEstudados = Math.max(0, cardsEstudados);
        this.ultimoDiaEstudo = ultimoDiaEstudo == null ? "" : ultimoDiaEstudo;
        this.fusoHorario = fusoHorario == null || fusoHorario.isEmpty() ? "UTC" : fusoHorario;
    }

    public String getUid() { return uid; }
    public String getNome() { return nome; }
    public String getFotoUrl() { return fotoUrl; }
    public List<String> getCursosChave() { return cursosChave; }

    /** Complementa o participante com os cursos dele (usado pelo filtro por curso do ranking). */
    public UsuarioRanking comCursos(List<String> cursos) {
        this.cursosChave = cursos == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(cursos));
        return this;
    }

    public long valor(TipoRanking tipo) {
        return valorEm(tipo, System.currentTimeMillis());
    }

    // busca o valor em um determinado tipo de ranking
    public long valorEm(TipoRanking tipo, long agora) {
        switch (tipo) {
            case PONTOS: return xp;
            case CARDS_ESTUDADOS: return cardsEstudados;
            default:
                TimeZone zona = TimeZone.getTimeZone(fusoHorario);
                return RegrasGamificacao.ofensivaVisivel(ofensiva, ultimoDiaEstudo,
                        RegrasGamificacao.dia(agora, zona, 0), RegrasGamificacao.dia(agora, zona, -1));
        }
    }
}
