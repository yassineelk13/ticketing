import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfirmDialog, ConfirmDialogData } from './confirm-dialog';

const data: ConfirmDialogData = {
  title: 'Annuler ce billet ?',
  message: 'Cette action est définitive.',
  confirmLabel: 'Annuler le billet',
  cancelLabel: 'Garder le billet',
};

describe('ConfirmDialog', () => {
  let results: unknown[];
  let fixture: ComponentFixture<ConfirmDialog>;

  const root = (): HTMLElement => fixture.nativeElement;
  const click = (selector: string): void => {
    root().querySelector<HTMLButtonElement>(selector)?.click();
  };

  beforeEach(async () => {
    results = [];
    TestBed.configureTestingModule({
      imports: [ConfirmDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close: (result: unknown) => results.push(result) } },
      ],
    });
    fixture = TestBed.createComponent(ConfirmDialog);
    await fixture.whenStable();
  });

  it('shows the title, the message and both labels', () => {
    const text = root().textContent ?? '';
    expect(text).toContain('Annuler ce billet ?');
    expect(text).toContain('Cette action est définitive.');
    expect(text).toContain('Garder le billet');
    expect(text).toContain('Annuler le billet');
  });

  it('closes with true when confirmed', () => {
    click('button.confirm');
    expect(results).toEqual([true]);
  });

  it('closes with false when dismissed', () => {
    click('button.dismiss');
    expect(results).toEqual([false]);
  });
});
