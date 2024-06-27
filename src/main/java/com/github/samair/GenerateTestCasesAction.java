package com.github.samair;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ui.Messages;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

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
            if (containingMethod != null) {
                System.out.println(containingMethod.getText());
                var response = this.llmClient.generateUnitTestCase(containingMethod.getText());
                VirtualFile virtualFile = FileEditorManager.getInstance(project).getOpenFiles()[0];
                PsiFile fileOpen = PsiManager.getInstance(project).findFile(virtualFile);
                PsiDirectory targetDirectory = fileOpen.getContainingDirectory();


                WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
                        var className  = javaFile.getClasses()[0];
                        String fileName =   className.getName()+"Test.java";
                        PsiFile newFile = targetDirectory.createFile(fileName);

                        if (newFile != null) {
                            PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
                            PsiJavaFile psiJavaFile = (PsiJavaFile) newFile;
                            PsiClass psiClass = elementFactory.createClassFromText(response, psiJavaFile);
                            psiClass.setName(className.getName()+"Test");
                            psiJavaFile.add(psiClass);

                            // Optionally reformat the code
                            CodeStyleManager.getInstance(project).reformat(newFile);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
            }
        }
        Messages.showMessageDialog(anActionEvent.getProject(), infoBuilder.toString(), "PSI Info", null);
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabled(editor != null && psiFile != null);
    }

}