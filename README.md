# 编译joana
```bash
git clone --depth=1 https://github.com/Anemone95/joana
```
按照README编译(只支持linux)
## Differ with Origin
与原版joana代码上没有不同，只是添加了stacktrace的行号和打包安装到maven仓库，具体来说，添加

```xml
<javac debug="true" optimize="off" debuglevel="lines,vars,source" .../>
```

到wala/joana.wala.core/build-debug.xml中

# 切片Demo
在项目目录下，编译
```bash
mvn clean package
```

运行切片：

```bash
java -cp joana-slice\target\joana-slice-1.0-SNAPSHOT.jar top.anemone.joana.target.joana.slice.SliceDemo
```

切片结果如下：

```java
1 :: ENTR :: entry :: null :: top.anemone.joana.target.Main.main(java.lang.String[])::CD,8:CD,12
8 :: CALL :: call :: Ljava/lang/String :: v6 = replace(v4)::CL,203
12 :: CALL :: call :: V :: sink(v6)::CL,225
203 :: ENTR :: entry :: null :: top.anemone.joana.target.Main.replace(java.lang.String)::CE,204:CD,207:CD,213:CD,219
204 :: EXIT :: exit :: Ljava/lang/String :: top.anemone.joana.target.Main.replace(java.lang.String)::
207 :: CALL :: call :: Ljava/lang/String :: v6 = p1 $str .replace(#(a), #(b))::
213 :: CALL :: call :: Ljava/lang/String :: v10 = v6.replace(#(c), #(d))::
219 :: NORM :: compound :: Ljava/lang/String :: return v10::DD,204
225 :: ENTR :: entry :: null :: top.anemone.joana.target.Main.sink(java.lang.String)::CD,229:CD,231:CD,235:CD,239:CD,244:CD,249:CD,253:CD,255
229 :: EXPR :: reference :: Ljava/io/PrintStream :: v3 = java.lang.System.out::
231 :: CALL :: call :: V :: v3.println(p1 $cmd )::
235 :: NORM :: declaration :: Ljava/lang/StringBuilder :: v5 = new java.lang.StringBuilder::
239 :: CALL :: call :: Ljava/lang/StringBuilder :: v8 = v5.append(p1 $cmd )::
244 :: CALL :: call :: Ljava/lang/StringBuilder :: v11 = v8.append(#(1))::
249 :: CALL :: call :: Ljava/lang/String :: v13 = v11.toString()::
253 :: EXPR :: reference :: Ljava/io/PrintStream :: v14 = java.lang.System.out::
255 :: CALL :: call :: V :: v14.println(v13)::
```
