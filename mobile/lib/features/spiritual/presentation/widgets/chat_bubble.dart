import 'package:flutter/material.dart';
import '../../../../app/colors.dart';
import '../../../../app/text_styles.dart';

class ChatBubble extends StatelessWidget {
  final String text;
  final bool isUser;
  final String time;

  const ChatBubble({
    super.key,
    required this.text,
    required this.isUser,
    required this.time,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    // Check if message contains Arabic text (to format it differently)
    final bool hasArabic = _containsArabic(text);

    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          // AI Avatar
          if (!isUser) ...[
            Container(
              width: 28,
              height: 28,
              margin: const EdgeInsets.only(right: 8, bottom: 6),
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  colors: [AppColors.primary, AppColors.primaryLight],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              alignment: Alignment.center,
              child: const Text(
                '☪',
                style: TextStyle(
                  color: AppColors.gold,
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ],
          
          // Main bubble
          Flexible(
            child: Container(
              margin: const EdgeInsets.symmetric(vertical: 6.0),
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
              constraints: BoxConstraints(
                maxWidth: MediaQuery.of(context).size.width * 0.76,
              ),
              decoration: BoxDecoration(
                gradient: isUser
                    ? const LinearGradient(
                        colors: [AppColors.primary, AppColors.primaryMid],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      )
                    : null,
                color: isUser
                    ? null
                    : (isDark ? AppColors.darkSurface : Colors.white),
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(16),
                  topRight: const Radius.circular(16),
                  bottomLeft: isUser ? const Radius.circular(16) : const Radius.circular(2),
                  bottomRight: isUser ? const Radius.circular(2) : const Radius.circular(16),
                ),
                border: isUser
                    ? null
                    : Border.all(
                        color: isDark ? AppColors.darkBorder : AppColors.border,
                        width: 0.5,
                      ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: isUser ? 0.12 : 0.03),
                    blurRadius: 8,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Text formatting (handle Arabic quotes specially)
                  if (hasArabic)
                    _buildFormattedTextWithArabic(text, isUser, isDark)
                  else
                    Text(
                      text,
                      style: AppTextStyles.bodyLarge.copyWith(
                        color: isUser ? Colors.white : (isDark ? AppColors.darkText : AppColors.textPrimary),
                        fontSize: 14.5,
                        height: 1.45,
                      ),
                    ),
                  const SizedBox(height: 4),
                  // Time stamp
                  Align(
                    alignment: Alignment.bottomRight,
                    child: Text(
                      time,
                      style: AppTextStyles.labelSmall.copyWith(
                        color: isUser
                            ? Colors.white.withValues(alpha: 0.5)
                            : (isDark ? AppColors.darkText2.withValues(alpha: 0.6) : AppColors.textTertiary),
                        fontSize: 10,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  // Detect Arabic script
  bool _containsArabic(String input) {
    for (int i = 0; i < input.length; i++) {
      int code = input.codeUnitAt(i);
      if (code >= 0x0600 && code <= 0x06FF) {
        return true;
      }
    }
    return false;
  }

  // Rich Text rendering for Arabic verses and translations
  Widget _buildFormattedTextWithArabic(String text, bool isUser, bool isDark) {
    // Split message by lines to style Arabic citations uniquely
    final lines = text.split('\n');
    final children = <Widget>[];

    for (var line in lines) {
      final trimmed = line.trim();
      if (_containsArabic(trimmed)) {
        children.add(
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4.0),
            child: Directionality(
              textDirection: TextDirection.rtl,
              child: Text(
                trimmed,
                textAlign: TextAlign.center,
                style: AppTextStyles.arabicMedium.copyWith(
                  color: isUser ? AppColors.goldLight : AppColors.gold,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
        );
      } else {
        children.add(
          Text(
            line,
            style: AppTextStyles.bodyLarge.copyWith(
              color: isUser ? Colors.white : (isDark ? AppColors.darkText : AppColors.textPrimary),
              fontSize: 14.5,
              height: 1.45,
            ),
          ),
        );
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: children,
    );
  }
}
