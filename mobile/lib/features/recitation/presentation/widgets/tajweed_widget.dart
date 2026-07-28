import 'package:flutter/material.dart';
import '../../../../app/colors.dart';
import '../../../../app/text_styles.dart';

class TajweedWidget extends StatelessWidget {
  final List<dynamic> errors;

  const TajweedWidget({super.key, required this.errors});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    if (errors.isEmpty) {
      return Card(
        color: AppColors.success.withValues(alpha: 0.08),
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.success, width: 1),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              const Icon(Icons.check_circle_rounded, color: AppColors.success, size: 40),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Mashalloh! Ajoyib tilovat.',
                      style: AppTextStyles.labelLarge.copyWith(
                        fontWeight: FontWeight.bold,
                        color: AppColors.primary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Hech qanday tajvid xatolari aniqlanmadi.',
                      style: AppTextStyles.bodyMedium.copyWith(
                        color: isDark ? AppColors.darkText2 : Colors.black54,
                      ),
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
        Text(
          'Aniqlangan xatolar:',
          style: AppTextStyles.headlineMedium.copyWith(
            color: isDark ? AppColors.darkText : AppColors.primary,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 12),
        ListView.separated(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          itemCount: errors.length,
          separatorBuilder: (context, index) => const SizedBox(height: 10),
          itemBuilder: (context, index) {
            final error = errors[index];
            final String type = (error['type'] ?? 'tajweed').toString().toLowerCase();
            final String word = error['word'] ?? '';
            final String desc = error['description'] ?? '';

            // Styling based on specs:
            // Tajweed: Yellow (#FFF3E0 + border #F0A030)
            // Makhraj: Pink (#FDE8E8 + border #E53E3E)
            final bool isMakhraj = type == 'makhraj';
            final Color bg = isMakhraj
                ? (isDark ? const Color(0x33E53E3E) : const Color(0xFFFDE8E8))
                : (isDark ? const Color(0x33F0A030) : const Color(0xFFFFF3E0));
            final Color border = isMakhraj ? const Color(0xFFE53E3E) : const Color(0xFFF0A030);

            return Container(
              decoration: BoxDecoration(
                color: bg,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: border, width: 1),
              ),
              child: ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: (isMakhraj ? AppColors.error : AppColors.warning).withValues(alpha: 0.15),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    isMakhraj ? Icons.record_voice_over_rounded : Icons.info_outline_rounded,
                    color: isMakhraj ? Colors.red : Colors.orange,
                  ),
                ),
                title: Text(
                  'So\'z: "$word" (${type.toUpperCase()})',
                  style: AppTextStyles.labelLarge.copyWith(
                    fontWeight: FontWeight.bold,
                    color: isDark ? Colors.white : AppColors.textPrimary,
                  ),
                ),
                subtitle: Text(
                  desc,
                  style: AppTextStyles.bodyMedium.copyWith(
                    color: isDark ? AppColors.darkText2 : Colors.black87,
                  ),
                ),
              ),
            );
          },
        ),
      ],
    );
  }
}
