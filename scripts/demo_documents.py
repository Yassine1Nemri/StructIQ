"""Deterministic fictional PDF fixtures, generated without external libraries."""


def pdf_bytes(pages):
    objects = [b'<< /Type /Catalog /Pages 2 0 R >>', b'', b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>']
    kids = []
    for text in pages:
        page_id = len(objects) + 1
        kids.append(f'{page_id} 0 R')
        escaped = text.replace('\\', '\\\\').replace('(', '\\(').replace(')', '\\)')
        stream = f'BT /F1 12 Tf 50 700 Td ({escaped}) Tj ET'.encode('ascii') if text else b''
        objects.append(f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents {page_id+1} 0 R >>'.encode())
        objects.append(f'<< /Length {len(stream)} >>\nstream\n'.encode() + stream + b'\nendstream')
    objects[1] = f'<< /Type /Pages /Kids [{" ".join(kids)}] /Count {len(kids)} >>'.encode()
    data = bytearray(b'%PDF-1.4\n')
    offsets = [0]
    for index, obj in enumerate(objects, 1):
        offsets.append(len(data))
        data.extend(f'{index} 0 obj\n'.encode() + obj + b'\nendobj\n')
    xref = len(data)
    data.extend(f'xref\n0 {len(objects)+1}\n0000000000 65535 f \n'.encode())
    for offset in offsets[1:]:
        data.extend(f'{offset:010d} 00000 n \n'.encode())
    data.extend(f'trailer\n<< /Size {len(objects)+1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n'.encode())
    return bytes(data)


def meeting_pdf():
    return pdf_bytes(['FICTIONAL Riverside meeting: engineer owns site inspection and safety review.',
                      'FICTIONAL actions: manager follows up with supplier; updated plans are pending.'])


if __name__ == '__main__':
    from pathlib import Path
    target = Path('/tmp/structiq-fictional-meeting.pdf')
    target.write_bytes(meeting_pdf())
    print(f'Fictional two-page PDF written to {target}')
