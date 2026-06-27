import 'package:flutter/material.dart';

class TajweedWidget extends StatelessWidget {
  final List<dynamic> errors;

  const TajweedWidget({super.key, required this.errors});

  @override
  Widget build(BuildContext context) {
    if (errors.isEmpty) {
      return Card(
        color: const Color(0xFF0F7A50).withOpacity(0.08),
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: Color(0xFF0F7A50), width: 1),
        ),
        child: const Padding(
          padding: EdgeInsets.all(20),
          child: Row(
            children: [
              Icon(Icons.check_circle, color: Color(0xFF0F7A50), size: 40),
              SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Mashalloh! Ajoyib tilovat.',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF1B4D3E),
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'Hech qanday tajvid xatolari aniqlanmadi.',
                      style: TextStyle(fontSize: 13, color: Colors.black54),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Aniqlangan xatolar:',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF1B4D3E)),
        ),
        const SizedBox(height: 12),
        ListView.separated(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          itemCount: errors.length,
          separatorBuilder: (context, index) => const SizedBox(height: 10),
          itemBuilder: (context, index) {
            final error = errors[index];
            final type = error['type'] ?? 'tajweed';
            final word = error['word'] ?? '';
            final desc = error['description'] ?? '';

            return Card(
              elevation: 2,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              child: ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.red.withOpacity(0.1),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.error_outline, color: Colors.red),
                ),
                title: Text(
                  'So\'z: "$word" (${type.toString().toUpperCase()})',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                subtitle: Text(
                  desc,
                  style: const TextStyle(fontSize: 13, color: Colors.black54),
                ),
              ),
            );
          },
        ),
      ],
    );
  }
}
