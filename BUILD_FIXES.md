# Correções realizadas no pacote

- Adicionado `.github/workflows/build-apk.yml`, que estava mencionado no README mas não existia no arquivo original.
- O workflow configura Java 17, Gradle 8.9 e Android SDK 35 e tenta compilar as variantes debug e release.
- Atualizado o README com instruções que correspondem aos arquivos presentes.
- Adicionado `.gitignore` para evitar incluir arquivos locais de build e configuração.

A revisão estática não encontrou erros de sintaxe Kotlin. Não foi possível confirmar uma compilação real neste ambiente porque ele não tem Android SDK nem Gradle instalados e não consegue baixar dependências; a compilação efetiva deve ser confirmada na execução do workflow do GitHub Actions.
