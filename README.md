# ShulkerPlus

Abre shulker boxes diretamente da mão — agachar + botão direito no ar — sem as pousar.
Feito de raiz para a suite `pt.henrique` (Paper/Purpur 1.21.x, Java 21).

## Porquê mais um plugin de shulkers?

As alternativas públicas ou são closed-source, ou estão paradas, ou não sabem nada
dos outros plugins deste servidor. Este sabe:

- **CombatLOG** — abrir shulkers em combate é negado (configurável).
- **BuildersDream** — itens `dream_item` nunca entram numa shulker (fecha o vetor
  de laundering por inventário-da-mão).

## Segurança anti-dupe

- A caixa aberta fica **presa ao slot** (click, drop, swap F, número, drag e place
  são todos cancelados enquanto a GUI está aberta).
- **Sem nesting**: shulkers nunca entram noutras shulkers pela GUI.
- **Stacks nunca abrem** (o Purpur permite oversized stacks; write-back num stack
  multiplicaria o conteúdo).
- Write-back **exatamente uma vez** (fecho, quit, morte — incluindo o caso do drop
  da caixa na morte), com tag de sessão em PDC; tags órfãs de um crash são limpas
  no join.

## Config

```yaml
require-sneak: true      # exigir agachar para abrir
play-sounds: true        # sons de abrir/fechar
allow-in-combat: false   # permitir abrir em combate (CombatLOG)
```

Permissões: `shulkerplus.use` (default true), `shulkerplus.admin` (op).
Comando: `/shulkerplus reload`.

## Build

```bash
mvn -B -DskipTests package   # → target/ShulkerPlus-1.0.0.jar
```

Licença: GPL-3.0
