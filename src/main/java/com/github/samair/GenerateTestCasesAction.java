package com.github.samair;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

import java.io.IOException;
import java.util.Collections;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;

public class GenerateTestCasesAction extends AnAction {

    private final LLMClient llmClient;

    public GenerateTestCasesAction() {
        this.llmClient = new LLMClient();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        var project = anActionEvent.getProject();
        Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = anActionEvent.getData(CommonDataKeys.PSI_FILE);
        PsiFile testFile;
        if (editor == null || psiFile == null) {
            return;
        }
        int offset = editor.getCaretModel().getOffset();

        final StringBuilder infoBuilder = new StringBuilder();
        PsiElement element = psiFile.findElementAt(offset);
        infoBuilder.append("Element at caret: ").append(element).append("\n");
        if (element != null) {
            PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            infoBuilder
                    .append("Containing method: ")
                    .append(containingMethod != null ? containingMethod.getName() : "none")
                    .append("\n");
            PsiJavaFile javaFile = (PsiJavaFile) psiFile;
            if (containingMethod != null) {
                System.out.println(containingMethod.getText());
                String response = getTestContent(javaFile, containingMethod);

                PsiClass psiClass = getPsiClassFromElement(element);
                testFile = PsiFileFactory.getInstance(project).createFileFromText(
                        psiClass.getName() + "Test.java",
                        psiClass.getLanguage(),
                        response);
                WriteCommandAction.runWriteCommandAction(project, () -> {
                VirtualFile testDir = getTestDirectory(project, javaFile);
                PsiManager.getInstance(project).findDirectory(testDir).add(testFile);
                });

                PsiClass containingClass = containingMethod.getContainingClass();
                infoBuilder
                        .append("Containing class: ")
                        .append(containingClass != null ? containingClass.getName() : "none")
                        .append("\n");

                infoBuilder.append("Local variables:\n");
                containingMethod.accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
                        super.visitLocalVariable(variable);
                        infoBuilder.append(variable.getName()).append("\n");
                    }
                });
            } else {
                testFile = null;
            }

            if (project != null && testFile != null) {
                runGradleTask(project, testFile.getName().split("\\.")[0]);
            }
        } else {
            testFile = null;
        }
        Messages.showMessageDialog(anActionEvent.getProject(), infoBuilder.toString(), "PSI Info", null);
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabled(editor != null && psiFile != null);
    }

    private VirtualFile getTestDirectory(Project project, PsiFile originalFile) {
        // Determine the test directory (adjust as per your project structure)
        VirtualFile sourceRoot = project.getBaseDir();
        VirtualFile testBase = sourceRoot.findFileByRelativePath("src/test/java");
        VirtualFile sourceBase = sourceRoot.findFileByRelativePath("src");
        if (testBase == null) {
            try {
                testBase = sourceBase.createChildDirectory(this, "test");

            } catch (IOException e) {
                // ignore
            }
            try {
                testBase = testBase.createChildDirectory(this, "java");
            } catch (IOException e) {
                // ignore
            }
        }
        PsiDirectory parent = originalFile.getParent();
        String parentDirPath = parent.toString();
        String testDirPath = parentDirPath.split(":")[1].replace("src/main", "src/test");
        String packagePath = testDirPath.split("/java/")[1];
        String[] packages = packagePath.split("/");
        for (String pkg : packages) {
            try {
                testBase = testBase.createChildDirectory(this, pkg);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return testBase;
    }

    private String getTestContent(PsiJavaFile javaFile, PsiMethod containingMethod) {
        var className = javaFile.getName().split("\\.")[0];
        PsiDirectory parent = javaFile.getParent();
        String parentDirPath = parent.toString();
        String packagePath = parentDirPath.split("/java/")[1];
        String packageName = packagePath.replace("/", ".");
        if (containingMethod != null) {
            System.out.println(containingMethod.getText());
            StringBuilder sb = new StringBuilder();
            sb.append("package ").append(packageName).append(";\n\n");
            sb.append("import org.junit.jupiter.api.Test;\n\n");
            sb.append("public class ").append(className).append("Test {\n\n");
            var response = this.llmClient.generateUnitTestCase(containingMethod.getText());
            if (response == null) {
                sb.append("\t@Test\n");
                sb.append("\tpublic void test").append(capitalize(containingMethod.getName())).append("() {\n");
                sb.append("\t\t// TODO: Implement test case for ").append(containingMethod.getName()).append("\n");
                sb.append("\t}\n\n");
            } else {
                sb.append(response);
            }
            sb.append("}\n\n");
            response = sb.toString();
            return response;
        } else {
            return null;
        }
    }

    private PsiClass getPsiClassFromElement(PsiElement element) {
        if (element instanceof PsiClass) {
            return (PsiClass) element;
        } else if (element.getParent() instanceof PsiClass) {
            return (PsiClass) element.getParent();
        } else if ((element instanceof PsiMethod || element.getParent() instanceof PsiMethod) && element.getParent().getParent() instanceof PsiClass) {
            return (PsiClass) element.getParent().getParent();
        }
        return null;
    }


    private void runGradleTask(Project project, String task) {
        var id = new ProjectSystemId("GRADLE").getId();
        ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
        settings.setExternalProjectPath(project.getBasePath());
        settings.setTaskNames(Collections.singletonList("test"));
        settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
        settings.setVmOptions("");
        ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project,  GradleConstants.SYSTEM_ID,
                new TaskCallback() {
                    @Override
                    public void onSuccess() {
                        System.out.println("Finished Gradle Task");
                    }

                    @Override
                    public void onFailure() {
                        Messages.showErrorDialog("Gradle task failed: ", "Error");
                    }


                }, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false);
    }
}