# Mesa de DJ com Threads

Aplicação de terminal em Java que simula uma mesa de DJ: cada faixa musical
(instrumento) toca em sua **própria thread**, de forma independente e
simultânea, e o DJ controla cada faixa em tempo real digitando comandos —
pausando, retomando, adicionando e removendo faixas sem afetar as demais.

As faixas de exemplo estão em `stems/` (`drums.wav`, `bass.wav`, `other.wav`),
carregadas no início como **bateria**, **baixo** e **synth**.

> música original: UCLA - Vulfmon x Louie Zong

---

## Equipe

- Lucas Bonfim Gomes
- Lucas Moreira de Carvalho
- Lucas Guilherme Pinheiro Valença Barbosa
- Raysa Costa Queiroz
- Rodrigo Morais Silvestri de Castro Montenegro
- Pablo Tamborini Nogueira
- Miguel Duarte de Barros
- Gabriel Cavalcante Barros de Oliveira

**Professor | Infraestrutura de Software - SO:** Raoni Monteiro de Oliveira

---

## Como compilar e rodar

Requer JDK 11+ e uma saída de áudio disponível no sistema.

Execute **a partir da raiz do projeto**, para que o caminho `stems/...` seja
encontrado:

```bash
javac -d bin src/*.java
java -cp bin Main
```

## Comandos

| Comando                | Efeito                                                        |
|------------------------|---------------------------------------------------------------|
| `play <faixa>`         | retoma a faixa de onde parou (ex: `play bateria`)             |
| `pause <faixa>`        | pausa a faixa, sem afetar as outras (ex: `pause baixo`)       |
| `remove <faixa>`       | encerra a faixa e a retira da mesa (ex: `remove synth`)       |
| `add <arquivo>`        | adiciona uma faixa de um arquivo (ex: `add stems/other.wav`)  |
| `add <nome> <arquivo>` | idem, com nome escolhido (ex: `add synth2 stems/other.wav`)   |
| `add <nome>`           | sem arquivo, gera um som sintetizado (ex: `add guitarra`)     |
| `list` / `status`      | o painel já mostra o status; o comando apenas força o refresh |
| `help`                 | lembra os comandos disponíveis                                |
| `exit` / `sair`        | encerra todas as faixas com segurança e sai                   |

Nomes de faixa não diferenciam maiúsculas de minúsculas. `rm` funciona como
atalho de `remove`.

---

## Conceitos da atividade e onde eles estão no código

### Threads independentes

`Track` implementa `Runnable`; cada instrumento roda em sua própria `Thread`
(criada em `Mixer.addTrack`), tocando seu áudio em loop contínuo. Como cada
faixa tem sua própria thread e sua própria linha de áudio (`Clip`), todas
soam ao mesmo tempo sem uma depender da outra.

### Início, pausa e encerramento controlados

Nenhuma thread é morta na força (`Thread.stop()` nunca é usado). Os comandos
apenas **sinalizam** uma mudança de estado, e a própria thread da faixa reage
a ela em um ponto seguro do seu loop:

- `pause()` muda o estado para `PAUSADO`; a thread para o áudio e dorme em
  `wait()`, sem gastar CPU;
- `resume()` volta o estado para `TOCANDO` e chama `notifyAll()`, acordando a
  thread, que retoma o áudio **de onde parou**;
- `requestStop()` levanta a flag `stopRequested`; a thread sai do loop e, no
  bloco `finally`, para e fecha o `Clip`, liberando o recurso de áudio.

Para os comandos responderem rápido, a espera pela duração do áudio é fatiada
em passos de 50ms (`sleepResponsive`), verificando entre eles se o DJ pediu
pausa ou remoção — em vez de dormir a música inteira de uma vez.

### Sincronização (`synchronized`)

Este é o ponto central da atividade. O estado de cada faixa é lido e escrito
por threads diferentes ao mesmo tempo:

| Estado compartilhado          | Quem escreve                         | Quem lê                              |
|-------------------------------|--------------------------------------|--------------------------------------|
| `Track.state`, `stopRequested`| thread principal (comandos do DJ)    | thread da faixa, thread do painel    |
| mapa de faixas do `Mixer`     | thread principal (`add` / `remove`)  | thread do painel (a cada 2s)         |
| a tela do terminal            | thread principal e thread do painel  | —                                    |

Cada um desses é protegido:

- **`Track`** — todos os métodos que tocam em `state` / `stopRequested` são
  `synchronized`, então apenas uma thread por vez modifica o estado de um
  instrumento. `wait()`/`notifyAll()` fazem a thread da faixa dormir enquanto
  pausada e acordar imediatamente ao ser retomada ou encerrada.
- **`Mixer`** — todo acesso ao mapa de faixas passa por um bloco
  `synchronized (lock)`, e `allTracks()` devolve uma **cópia** da lista. Sem
  isso, o painel poderia percorrer o mapa exatamente enquanto uma faixa é
  adicionada ou removida (`ConcurrentModificationException`).
- **`Console`** — as escritas na tela também são serializadas por um lock, para
  que o redesenho do painel nunca seja cortado no meio pela resposta de um
  comando (o que embaralharia as sequências ANSI e quebraria o layout).

---

## Desafios extras implementados

### Painel de status ao vivo

Uma thread dedicada (`StatusPanel`) pede o redesenho do painel a cada 2
segundos, mostrando nome, estado e origem de cada faixa.

### Adicionar instrumento durante a execução

O comando `add` cria uma faixa nova, com sua thread, enquanto a música já está
tocando. Aceita um arquivo de áudio (`add stems/other.wav`), um nome + arquivo
(`add synth2 stems/other.wav`) ou apenas um nome, caso em que um pequeno som
é sintetizado na hora pelo `ToneGenerator` (`add guitarra`).

> O desafio opcional de BPM não foi realizado.

---

## Início sincronizado

As faixas iniciais esperam em um **portão de largada** (`CountDownLatch` no
`Mixer`) antes de tocar. Cada uma abre e carrega seu `Clip` — operação lenta e
de duração variável — e então bloqueia no portão. Só depois que as três estão
prontas o `Main` chama `mixer.releaseAll()`, e todas chamam `clip.start()`
praticamente no mesmo instante, em vez de começarem escalonadas conforme cada
thread é criada. Faixas adicionadas depois com `add` recebem o mesmo portão,
já aberto, e começam a tocar imediatamente.

Vale notar o limite disso: o portão alinha a **largada**. Ele não corrige
deriva de fase entre faixas de durações diferentes ao longo de muitas
repetições, nem realinha uma faixa depois que ela é pausada e retomada — ao
retomar, ela volta de onde parou, então fica deslocada em relação às outras.
Um alinhamento contínuo exigiria um relógio de compasso compartilhado, o que
está fora do escopo da atividade.

---

## Layout fixo do terminal (decisão de UX)

Um painel que se atualiza sozinho tem um efeito colateral incômodo: se ele
limpar e reimprimir a tela inteira enquanto você digita, seu texto some ou
embaralha, e você precisa correr para digitar antes do próximo refresh.

Para resolver isso, o `Console` usa um **layout de tela fixo** com códigos
ANSI/VT100:

- o painel sempre ocupa as mesmas linhas do topo, escritas por posicionamento
  absoluto — a tela nunca rola;
- a linha de comando fica em uma linha própria, mais abaixo, onde o painel
  **nunca** escreve;
- antes de redesenhar, a posição do cursor é salva (`ESC 7`) e, ao final,
  restaurada (`ESC 8`).

Assim o painel pisca sozinho a cada 2 segundos enquanto o comando que você
está digitando permanece intacto, mesmo sem ter apertado Enter. As respostas
aos comandos também aparecem dentro do painel, em vez de rolarem a tela.

---

## Estrutura

```
stems/                  faixas de áudio de exemplo (drums.wav, bass.wav, other.wav)
src/Main.java           thread principal: carrega as faixas e lê os comandos do DJ
src/Track.java          um instrumento/faixa: thread + estado sincronizado
src/TrackState.java     enum dos estados (TOCANDO / PAUSADO / PARADO)
src/Mixer.java          registro thread-safe das faixas + portão de largada
src/Console.java        layout fixo do terminal (painel + linha de comando)
src/StatusPanel.java    thread que atualiza o painel a cada 2 segundos
src/ToneGenerator.java  som sintetizado para instrumentos adicionados sem arquivo
```

## Observações

- O painel usa ANSI/VT100 (posicionamento de cursor, salvar/restaurar posição).
  Precisa de um terminal com esse suporte — Linux, macOS e Windows Terminal
  funcionam; alguns consoles embutidos de IDE não emulam VT100 corretamente.
  O terminal precisa ter ao menos ~16 linhas visíveis.
- Se o sistema não tiver dispositivo de áudio disponível, o carregamento de uma
  faixa pode falhar. O erro é reportado por faixa e não derruba o programa.
