via vscode *pick the version you're developing with *

```
ctrl + shift + p --> Java: Configure Java Runtime
```
## build on linux

```
source jdk.sh --> choose the jdk
for java 8:    mvn clean package
for java 21:   mvn clean package -Pjava21
```

## build on windows

```
double click jdk.bat --> choose the jdk
for java 8:    mvn clean package
for java 21:   mvn clean package -Pjava21
```

### simple vault client testing

```bash
mvn clean package
 java -cp target/aieutil-1.0.0.jar com.baml.mav.aieutil.validate.VaultClientTest
```