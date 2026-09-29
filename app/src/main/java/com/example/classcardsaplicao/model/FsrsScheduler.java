package com.example.classcardsaplicao.model;

public class FsrsScheduler {

    private final double[] w;

    private final double retencaoDesejada;
    private final int intervaloMaximo;

    // essa classe é onde é calculada a repetição espaçada, no construtor ele pega os valores da classe FsrsParametros

    public FsrsScheduler() {
        this.w = FsrsParametros.W;
        this.retencaoDesejada = FsrsParametros.RETENCAO_DESEJADA;

        this.intervaloMaximo = FsrsParametros.INTERVALO_MAXIMO_DIAS;
    }

    // para o calculo de repetição espaçada é necessario calcular 3 valores

    // R é a probabilidade estimada de você conseguir lembrar de um flashcard agora
    // quando tu acerta um flash card, o R aumenta. Conforme o tempo passa sem revisão, ele diminui

    // o S ele calcula quanto tempo demora para o R diminuir.
    // o s também é dividido em "após esquecer" e "após relembrar", que são dois valores dependendo em que momento que tu acertou esse card

    // o d é para a dificuldade do card. Ele pega isso com base no historico, e não só se a pessoa botou que não conseguiu lembrar uma vez
    public double calcularEstabilidadeInicial(Rating rating) {

        // aqui ele passa o Rating da resposta (facil, dificil, não lembrei, etc) pro metodo e com isso calcula o S

        int indice = rating.getValor() - 1;

        return Math.max(w[indice], 0.1);
    }


    // calcula o d inicial
    public double calcularDificuldadeInicial(Rating rating) {

        double dificuldade = w[4] - w[5] * (rating.getValor() - 3);

        return limitarDificuldade(dificuldade);
    }

    // aqui é a formula utilizada pra calcular o R

    public double calcularRecuperabilidade(double estabilidade, double diasDesdeUltimaRevisao) {

        if (estabilidade <= 0) {
            return 0;
        }

        double decay = w[20]; // aqui ele pega o esquecimento esperado na nossa classe de parametros

        double factor = Math.pow(0.9, -1.0 / decay) - 1;

        return Math.pow(1 + factor * diasDesdeUltimaRevisao / estabilidade, -decay);
    }

    // o intervalo é quando o card deve ser estudado de novov
    public int calcularProximoIntervalo(double estabilidade) {

        double decay = w[20];

        double factor = Math.pow(0.9, -1.0 / decay) - 1;

        double intervalo = estabilidade / factor * (Math.pow(retencaoDesejada, -1.0 / decay) - 1);

        long arredondado = Math.round(intervalo);

        return (int) Math.min(Math.max(arredondado, 1), intervaloMaximo);
    }

    private double limitarDificuldade(double dificuldade) {
        return Math.min(Math.max(dificuldade, 1.0), 10.0);
    }

    public double calcularNovaDificuldade(double dificuldade, Rating rating) {

        double delta = -w[6] * (rating.getValor() - 3);

        double linearDamping = delta * (10.0 - dificuldade) / 9.0;

        double dificuldadeTemporaria = dificuldade + linearDamping;


        double dificuldadeEasy = calcularDificuldadeInicial(Rating.EASY);

        double novaDificuldade = w[7] * dificuldadeEasy + (1 - w[7]) * dificuldadeTemporaria;

        return limitarDificuldade(novaDificuldade);
    }

    public double calcularEstabilidadeAposLembrar(double dificuldade, double estabilidade, double recuperabilidade, Rating rating) {

        // aqui é um ternario, ele ve se o rating é hard, se sim ele pega o primeiro valor, se não, pega o segundo
        double penalidadeHard = rating == Rating.HARD ? w[15] : 1.0;

        double bonusEasy = rating == Rating.EASY ? w[16] : 1.0;

        double aumento = Math.exp(w[8])
                        * (11 - dificuldade)
                        * Math.pow(estabilidade, -w[9])
                        * (Math.exp(w[10] * (1 - recuperabilidade)) - 1)
                        * penalidadeHard
                        * bonusEasy;

        return estabilidade * (1 + aumento);
    }

    public double calcularEstabilidadeAposEsquecer(double dificuldade, double estabilidade, double recuperabilidade
    ) {

        double novaEstabilidade = w[11]
                        * Math.pow(dificuldade, -w[12])
                        * (Math.pow(estabilidade + 1, w[13]) - 1)
                        * Math.exp(w[14] * (1 - recuperabilidade));

        return Math.min(novaEstabilidade, estabilidade);
    }

    public double calcularEstabilidadeMesmoDia(double estabilidade, Rating rating) {

        double aumento = Math.exp(w[17] * (rating.getValor() - 3 + w[18]))
                        * Math.pow(estabilidade, -w[19]);

        // Em respostas corretas, não permitimos
        // redução da estabilidade.

        if (rating.getValor() >= Rating.GOOD.getValor()) {
            aumento = Math.max(aumento, 1.0);
        }

        return estabilidade * aumento;
    }
    public void revisarNovoFlashcard(ProgressoFlashcard progresso, Rating rating, long agora) {

        double estabilidade = calcularEstabilidadeInicial(rating);

        double dificuldade = calcularDificuldadeInicial(rating);

        progresso.setStability(estabilidade);
        progresso.setDifficulty(dificuldade);

        progresso.setUltimaRevisao(agora);
        progresso.setRepeticoes(progresso.getRepeticoes() + 1);


        // o lapso é quando a pessoa erra um card
        if (rating == Rating.AGAIN) {
            progresso.setLapsos(progresso.getLapsos() + 1);
        }

        int intervalo = calcularProximoIntervalo(estabilidade);

        if (rating == Rating.EASY) {

            progresso.setDiasAgendados(intervalo);

            progresso.setProximaRevisao(agora + diasParaMillis(intervalo));

            progresso.setEstado(EstadoFlashcard.REVIEW.name());

        } else {

            long minutos;

            // dependendo do rating ele pega na classe os minutos diferentes

            switch (rating) {

                case AGAIN:
                    minutos = FsrsParametros.PASSO_AGAIN_MINUTOS;
                    break;

                case HARD:
                    minutos = FsrsParametros.PASSO_HARD_MINUTOS;
                    break;

                default:
                    minutos = FsrsParametros.PASSO_GOOD_MINUTOS;
            }

            progresso.setDiasAgendados(0);

            progresso.setProximaRevisao(agora + minutosParaMillis(minutos));

            progresso.setEstado(EstadoFlashcard.LEARNING.name());
        }

        progresso.setEstado(EstadoFlashcard.REVIEW.name());
    }

    // metodos para converter para milisegundos

    private long minutosParaMillis(long minutos) {
        return minutos * 60L * 1000L;
    }

    private long diasParaMillis(long dias) {
        return dias * 24L * 60L * 60L * 1000L;
    }

    // o card pode ter esses estados: new, learning, review e relearning, esses estados são usados para o calculo da repetição espaçada

    public void revisarFlashcardEmAprendizagem(ProgressoFlashcard progresso, Rating rating, long agora) {

        double estabilidadeAtual = progresso.getStability();


        double novaEstabilidade = calcularEstabilidadeMesmoDia(estabilidadeAtual, rating);

        double novaDificuldade = calcularNovaDificuldade(progresso.getDifficulty(), rating);

        progresso.setStability(novaEstabilidade);
        progresso.setDifficulty(novaDificuldade);

        progresso.setUltimaRevisao(agora);

        progresso.setRepeticoes(progresso.getRepeticoes() + 1);

        if (rating == Rating.AGAIN) {

            progresso.setProximaRevisao(agora + minutosParaMillis(FsrsParametros.PASSO_AGAIN_MINUTOS));

            progresso.setEstado(EstadoFlashcard.LEARNING.name());

            return;
        }

        if (rating == Rating.HARD) {

            progresso.setProximaRevisao(agora + minutosParaMillis(FsrsParametros.PASSO_HARD_MINUTOS));

            progresso.setEstado(EstadoFlashcard.LEARNING.name());

            return;
        }


        // no caso de good ou easy o card sai do aprendizado e entra em "review".

        int intervalo = calcularProximoIntervalo(novaEstabilidade);

        progresso.setDiasAgendados(intervalo);

        progresso.setProximaRevisao(
                agora + diasParaMillis(intervalo)
        );

        progresso.setEstado(EstadoFlashcard.REVIEW.name());
    }

    public void revisarFlashcardEmReview(ProgressoFlashcard progresso, Rating rating, long agora) {

        long ultimaRevisao = progresso.getUltimaRevisao();

        double diasDecorridos = (agora - ultimaRevisao) / (1000.0 * 60 * 60 * 24);

        diasDecorridos = Math.max(diasDecorridos, 0);

        progresso.setDiasDecorridos((int) Math.floor(diasDecorridos));

        double estabilidadeAtual = progresso.getStability();

        double dificuldadeAtual = progresso.getDifficulty();

        double recuperabilidade = calcularRecuperabilidade(estabilidadeAtual, diasDecorridos);

        double novaEstabilidade;

        // aqui é quando o usuario esqueceu um card já aprendido, já que ele errou no estado de review

        if (rating == Rating.AGAIN) {

            novaEstabilidade = calcularEstabilidadeAposEsquecer(dificuldadeAtual, estabilidadeAtual, recuperabilidade);

            progresso.setLapsos(progresso.getLapsos() + 1);

        } else {

            novaEstabilidade = calcularEstabilidadeAposLembrar(dificuldadeAtual, estabilidadeAtual, recuperabilidade, rating);
        }

        double novaDificuldade = calcularNovaDificuldade(dificuldadeAtual, rating);

        progresso.setStability(novaEstabilidade);
        progresso.setDifficulty(novaDificuldade);

        progresso.setUltimaRevisao(agora);

        progresso.setRepeticoes(
                progresso.getRepeticoes() + 1
        );

        // again entra em reaprendizagem

        if (rating == Rating.AGAIN) {

            progresso.setDiasAgendados(0);

            progresso.setProximaRevisao(agora + minutosParaMillis(FsrsParametros.PASSO_AGAIN_MINUTOS));

            progresso.setEstado(EstadoFlashcard.RELEARNING.name());

            return;
        }

        // HARD / GOOD / EASY continuam em REVIEW

        int intervalo = calcularProximoIntervalo(novaEstabilidade);

        progresso.setDiasAgendados(intervalo);

        progresso.setProximaRevisao(agora + diasParaMillis(intervalo));

        progresso.setEstado(EstadoFlashcard.REVIEW.name());
    }

    public void revisarFlashcardEmReaprendizagem(ProgressoFlashcard progresso, Rating rating, long agora) {

        double estabilidadeAtual = progresso.getStability();

        double novaEstabilidade = calcularEstabilidadeMesmoDia(estabilidadeAtual, rating);

        double novaDificuldade = calcularNovaDificuldade(progresso.getDifficulty(), rating);

        progresso.setStability(novaEstabilidade);
        progresso.setDifficulty(novaDificuldade);

        progresso.setUltimaRevisao(agora);

        progresso.setRepeticoes(progresso.getRepeticoes() + 1
        );

        if (rating == Rating.AGAIN) {

            progresso.setDiasAgendados(0);

            progresso.setProximaRevisao(agora + minutosParaMillis(FsrsParametros.PASSO_AGAIN_MINUTOS));

            progresso.setEstado(EstadoFlashcard.RELEARNING.name());

            return;
        }

        // no hard ele continua reaprendendo
        if (rating == Rating.HARD) {

            progresso.setDiasAgendados(0);

            progresso.setProximaRevisao(agora + minutosParaMillis(FsrsParametros.PASSO_HARD_MINUTOS));

            progresso.setEstado(EstadoFlashcard.RELEARNING.name());

            return;
        }

        // no good ou easy ele retorna pro ciclo normal de revisão

        int intervalo = calcularProximoIntervalo(novaEstabilidade);

        progresso.setDiasAgendados(intervalo);

        progresso.setProximaRevisao(agora + diasParaMillis(intervalo));

        progresso.setEstado(EstadoFlashcard.REVIEW.name());
    }

    public void revisar(ProgressoFlashcard progresso, Rating rating) {

        long agora = System.currentTimeMillis();

        EstadoFlashcard estado;

        // pra caso o estado seja nulo, ele vira new

        try {
            estado = EstadoFlashcard.valueOf(progresso.getEstado());
        } catch (Exception e) {
            estado = EstadoFlashcard.NEW;
        }

        switch (estado) {

            case NEW:
                revisarNovoFlashcard(progresso, rating, agora);
                break;

            case LEARNING:
                revisarFlashcardEmAprendizagem(progresso, rating, agora);
                break;

            case REVIEW:
                revisarFlashcardEmReview(progresso, rating, agora);
                break;

            case RELEARNING:
                revisarFlashcardEmReaprendizagem(progresso, rating, agora);
                break;
        }
    }

}