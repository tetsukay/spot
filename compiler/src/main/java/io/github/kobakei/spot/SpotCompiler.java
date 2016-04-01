package io.github.kobakei.spot;

import android.content.Context;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import io.github.kobakei.spot.annotation.PrefBoolean;
import io.github.kobakei.spot.annotation.PrefFloat;
import io.github.kobakei.spot.annotation.PrefInt;
import io.github.kobakei.spot.annotation.PrefLong;
import io.github.kobakei.spot.annotation.PrefString;
import io.github.kobakei.spot.annotation.PrefStringSet;
import io.github.kobakei.spot.annotation.Table;
import io.github.kobakei.spot.internal.PreferencesUtil;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "io.github.kobakei.spot.annotation.Table",
        "io.github.kobakei.spot.annotation.PrefInt",
        "io.github.kobakei.spot.annotation.PrefLong",
        "io.github.kobakei.spot.annotation.PrefFloat",
        "io.github.kobakei.spot.annotation.PrefBoolean",
        "io.github.kobakei.spot.annotation.PrefString",
        "io.github.kobakei.spot.annotation.PrefStringSet"
})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class SpotCompiler extends AbstractProcessor {

    private Filer filer;
    private Messager messager;
    private Elements elements;

    private List<Element> tableElements;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elements = processingEnv.getElementUtils();

        this.tableElements = new ArrayList<>();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        log("*** process START ***");
        Class<Table> tableClass = Table.class;
        for (Element element : roundEnv.getElementsAnnotatedWith(tableClass)) {
            ElementKind kind = element.getKind();
            if (kind == ElementKind.CLASS) {
                try {
                    log("*** Found table. Generating repository ***");
                    generateRepositoryClass(element);
                    this.tableElements.add(element);
                } catch (IOException e) {
                    logError("IO error");
                }
            } else {
                logError("Type error");
            }
        }

        log("*** process END ***");

        return true;
    }

    /**
     * Generate YourModel$$Repository class
     * @param element
     * @throws IOException
     */
    private void generateRepositoryClass(Element element) throws IOException {
        String packageName = elements.getPackageOf(element).getQualifiedName().toString();
        ClassName entityClass = ClassName.get(packageName, element.getSimpleName().toString());
        ClassName stringClass = ClassName.get(String.class);
        ClassName contextClass = ClassName.get(Context.class);

        Table tableAnnotation = element.getAnnotation(Table.class);
        String tableName = tableAnnotation.name();

        MethodSpec constructorSpec = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build();

        MethodSpec getNameSpec = MethodSpec.methodBuilder("getName")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .returns(stringClass)
                .addStatement("return $S", tableName)
                .build();

        MethodSpec.Builder getEntitySpecBuilder = MethodSpec.methodBuilder("getEntity")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                .addParameter(contextClass, "context")
                .returns(entityClass)
                .addStatement("$T entity = new $T()", entityClass, entityClass);

        MethodSpec.Builder putEntitySpecBuilder = MethodSpec.methodBuilder("putEntity")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                .addParameter(contextClass, "context")
                .addParameter(entityClass, "entity");

        for (Element element1 : element.getEnclosedElements()) {
            handlePrefInt(element1, getEntitySpecBuilder, putEntitySpecBuilder);
            handlePrefString(element1, getEntitySpecBuilder, putEntitySpecBuilder);
            handlePrefLong(element1, getEntitySpecBuilder, putEntitySpecBuilder);
            handlePrefFloat(element1, getEntitySpecBuilder, putEntitySpecBuilder);
            handlePrefBoolean(element1, getEntitySpecBuilder, putEntitySpecBuilder);
            handlePrefStringSet(element1, getEntitySpecBuilder, putEntitySpecBuilder);
        }

        getEntitySpecBuilder.addStatement("return entity");

        String className = element.getSimpleName() + "SpotRepository";
        TypeSpec repository = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(constructorSpec)
                .addMethod(getNameSpec)
                .addMethod(getEntitySpecBuilder.build())
                .addMethod(putEntitySpecBuilder.build())
                .build();

        JavaFile.builder(packageName, repository)
                .build()
                .writeTo(filer);
    }

    private void handlePrefInt(Element element1, MethodSpec.Builder getEntitySpecBuilder,
                               MethodSpec.Builder putEntitySpecBuilder) {
        ClassName utilClass = ClassName.get(PreferencesUtil.class);
        if (element1.getAnnotation(PrefInt.class) != null) {
            PrefInt prefInt = element1.getAnnotation(PrefInt.class);

            getEntitySpecBuilder.addStatement(
                    "entity.$N = $T.getInt(context, getName(), $S, $L)",
                    element1.getSimpleName(),
                    utilClass,
                    prefInt.name(),
                    prefInt.defaultValue());

            putEntitySpecBuilder.addStatement(
                    "$T.putInt(context, getName(), $S, entity.$N)",
                    utilClass,
                    prefInt.name(),
                    element1.getSimpleName());
        }
    }

    private void handlePrefLong(Element element1, MethodSpec.Builder getEntitySpecBuilder,
                               MethodSpec.Builder putEntitySpecBuilder) {
        ClassName utilClass = ClassName.get(PreferencesUtil.class);
        if (element1.getAnnotation(PrefLong.class) != null) {
            PrefLong pref = element1.getAnnotation(PrefLong.class);

            getEntitySpecBuilder.addStatement(
                    "entity.$N = $T.getLong(context, getName(), $S, $L)",
                    element1.getSimpleName(),
                    utilClass,
                    pref.name(),
                    pref.defaultValue());

            putEntitySpecBuilder.addStatement(
                    "$T.putLong(context, getName(), $S, entity.$N)",
                    utilClass,
                    pref.name(),
                    element1.getSimpleName());
        }
    }

    private void handlePrefFloat(Element element1, MethodSpec.Builder getEntitySpecBuilder,
                                  MethodSpec.Builder putEntitySpecBuilder) {
        ClassName utilClass = ClassName.get(PreferencesUtil.class);
        if (element1.getAnnotation(PrefFloat.class) != null) {
            PrefFloat pref = element1.getAnnotation(PrefFloat.class);

            getEntitySpecBuilder.addStatement(
                    "entity.$N = $T.getFloat(context, getName(), $S, $Lf)",
                    element1.getSimpleName(),
                    utilClass,
                    pref.name(),
                    pref.defaultValue());

            putEntitySpecBuilder.addStatement(
                    "$T.putFloat(context, getName(), $S, entity.$N)",
                    utilClass,
                    pref.name(),
                    element1.getSimpleName());
        }
    }

    private void handlePrefBoolean(Element element1, MethodSpec.Builder getEntitySpecBuilder,
                                   MethodSpec.Builder putEntitySpecBuilder) {
        ClassName utilClass = ClassName.get(PreferencesUtil.class);
        if (element1.getAnnotation(PrefBoolean.class) != null) {
            PrefBoolean pref = element1.getAnnotation(PrefBoolean.class);

            getEntitySpecBuilder.addStatement(
                    "entity.$N = $T.getBoolean(context, getName(), $S, $L)",
                    element1.getSimpleName(),
                    utilClass,
                    pref.name(),
                    pref.defaultValue());

            putEntitySpecBuilder.addStatement(
                    "$T.putBoolean(context, getName(), $S, entity.$N)",
                    utilClass,
                    pref.name(),
                    element1.getSimpleName());
        }
    }

    private void handlePrefString(Element element1, MethodSpec.Builder getEntitySpecBuilder,
                                  MethodSpec.Builder putEntitySpecBuilder) {
        ClassName utilClass = ClassName.get(PreferencesUtil.class);
        if (element1.getAnnotation(PrefString.class) != null) {
            PrefString prefString = element1.getAnnotation(PrefString.class);

            getEntitySpecBuilder.addStatement(
                    "entity.$N = $T.getString(context, getName(), $S, $S)",
                    element1.getSimpleName(),
                    utilClass,
                    prefString.name(),
                    prefString.defaultValue());

            putEntitySpecBuilder.addStatement(
                    "$T.putString(context, getName(), $S, entity.$N)",
                    utilClass,
                    prefString.name(),
                    element1.getSimpleName());
        }
    }

    private void handlePrefStringSet(Element element1, MethodSpec.Builder getEntitySpecBuilder,
                                     MethodSpec.Builder putEntitySpecBuilder) {
        ClassName utilClass = ClassName.get(PreferencesUtil.class);
        if (element1.getAnnotation(PrefStringSet.class) != null) {
            PrefStringSet pref = element1.getAnnotation(PrefStringSet.class);

            getEntitySpecBuilder.addStatement(
                    "entity.$N = $T.getStringSet(context, getName(), $S, null)",
                    element1.getSimpleName(),
                    utilClass,
                    pref.name());

            putEntitySpecBuilder.addStatement(
                    "$T.putStringSet(context, getName(), $S, entity.$N)",
                    utilClass,
                    pref.name(),
                    element1.getSimpleName());
        }
    }

    private void log(String msg) {
        this.messager.printMessage(Diagnostic.Kind.OTHER, msg);
    }

    private void logError(String msg) {
        this.messager.printMessage(Diagnostic.Kind.ERROR, msg);
    }
}