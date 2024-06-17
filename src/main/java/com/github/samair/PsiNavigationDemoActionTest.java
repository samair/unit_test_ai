class PsiNavigationDemoActionTest {
    @Test
    public void testUpdateEnabled() {
        AnActionEvent mockEvent = mock(AnActionEvent.class);
        Editor mockEditor = mock(Editor.class);
        PsiFile mockPsiFile = mock(PsiFile.class);
        Presentation mockPresentation = mock(Presentation.class);

        when(mockEvent.getData(CommonDataKeys.EDITOR)).thenReturn(mockEditor);
        when(mockEvent.getData(CommonDataKeys.PSI_FILE)).thenReturn(mockPsiFile);
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        MyClass myClass = new MyClass();
        myClass.update(mockEvent);

        verify(mockPresentation).setEnabled(true);
    }

    @Test
    public void testUpdateDisabled() {
        AnActionEvent mockEvent = mock(AnActionEvent.class);
        Presentation mockPresentation = mock(Presentation.class);

        when(mockEvent.getData(CommonDataKeys.EDITOR)).thenReturn(null);
        when(mockEvent.getData(CommonDataKeys.PSI_FILE)).thenReturn(null);
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        MyClass myClass = new MyClass();
        myClass.update(mockEvent);

        verify(mockPresentation).setEnabled(false);
    }
}