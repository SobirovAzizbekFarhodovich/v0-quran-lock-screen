import 'package:flutter/material.dart';
import '../../../../app/colors.dart';
import '../../../../app/text_styles.dart';

class SourceCard extends StatelessWidget {
  final String book;
  final String chapter;

  const SourceCard({
    super.key,
    required this.book,
    required this.chapter,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      margin: const EdgeInsets.only(left: 36, top: 4, bottom: 8),
      decoration: BoxDecoration(
        color: AppColors.gold.withOpacity(0.08),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: AppColors.gold.withOpacity(0.25),
          width: 0.5,
        ),
      ),
      child: IntrinsicHeight(
        child: Row(
          children: [
            // Left 3px gold vertical highlight line
            Container(
              width: 3,
              decoration: const BoxDecoration(
                color: AppColors.gold,
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(10),
                  bottomLeft: Radius.circular(10),
                ),
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      'MANBA',
                      style: AppTextStyles.labelSmall.copyWith(
                        color: AppColors.gold,
                        fontSize: 9,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1.0,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '$book · $chapter',
                      style: AppTextStyles.labelMedium.copyWith(
                        color: isDark ? AppColors.darkText : AppColors.textPrimary,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
