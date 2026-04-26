init -999 python:
    import re
    from jnius import autoclass
    SDLActivity = autoclass('org.libsdl.app.SDLActivity')
    context = SDLActivity.getContext()
    prefs = context.getSharedPreferences("RenPlayPrefs", 0)
    renplay_trans_enabled = prefs.getBoolean("enable_translation", False)

    if renplay_trans_enabled:
        Translator = autoclass('ru.reset.renplay.utils.RenPlayTranslator')
        Translator.initializeFromPrefs(context)
        renplay_trans_cache = {}
        renplay_pending = set()
        renplay_tag_map = {}
        import weakref
        renplay_tracked_texts = weakref.WeakSet()
        
        try:
            renplay_basestring = basestring
        except NameError:
            renplay_basestring = str
            
        def hide_tags(text):
            clean_text = ''
            tag_map = {}
            tag_counter = 0
            i = 0
            length = len(text)
            while i < length:
                c = text[i]
                if c == '\\' and i + 1 < length:
                    clean_text += c + text[i+1]
                    i += 2
                    continue
                if c == '{':
                    start = i
                    while i < length and text[i] != '}':
                        i += 1
                    if i < length:
                        i += 1
                    tag = text[start:i]
                    marker = 'XTAG' + str(tag_counter) + 'X'
                    tag_map[marker] = tag
                    clean_text += marker
                    tag_counter += 1
                    continue
                if c == '[':
                    start = i
                    while i < length and text[i] != ']':
                        i += 1
                    if i < length:
                        i += 1
                    tag = text[start:i]
                    marker = 'XTAG' + str(tag_counter) + 'X'
                    tag_map[marker] = tag
                    clean_text += marker
                    tag_counter += 1
                    continue
                clean_text += c
                i += 1
            return clean_text, tag_map

        def restore_tags(text, tag_map):
            import re
            for marker, tag in tag_map.items():
                if marker in text:
                    text = text.replace(marker, tag)
                else:
                    pattern_str = r'\s*'.join(list(marker))
                    pattern = re.compile(pattern_str, re.IGNORECASE)
                    text = pattern.sub(tag, text, 1)
            return text
            
        def is_translatable(clean_text):
            if not clean_text or not clean_text.strip(): return False
            has_letter = False
            for c in clean_text:
                if c.isalpha():
                    has_letter = True
                    break
            if not has_letter: return False
            low = clean_text.lower()
            for ext in ['.png', '.jpg', '.jpeg', '.webp', '.ogg', '.mp3', '.wav', '.rpy', '.rpym', '.ttf', '.otf']:
                if ext in low: return False
            if '/' in clean_text and '_' in clean_text: return False
            return True
            
        renplay_old_text_init = renpy.text.text.Text.__init__
        def renplay_new_text_init(self, text, *args, **kwargs):
            try:
                renplay_old_text_init(self, text, *args, **kwargs)

                my_scope = args[1] if len(args) > 1 else kwargs.get('scope', None)
                my_subst = args[2] if len(args) > 2 else kwargs.get('substitute', None)
                if my_subst is None:
                    my_subst = getattr(renpy.config, "new_substitutions", True)

                self.renplay_substitute = my_subst
                self.renplay_scope = my_scope

                orig_text = ''
                if isinstance(text, renplay_basestring):
                    orig_text = text
                elif isinstance(text, list):
                    orig_text = ''.join([t for t in text if isinstance(t, renplay_basestring)])
                if orig_text and orig_text.strip():
                    self.renplay_orig_text = orig_text
                    renplay_tracked_texts.add(self)
                    if orig_text in renplay_trans_cache:
                        trans_text = renplay_trans_cache[orig_text]
                        if self.renplay_substitute:
                            try:
                                import renpy.substitutions
                                trans_text, _ = renpy.substitutions.substitute(trans_text, self.renplay_scope)
                            except Exception:
                                pass
                        self.set_text([trans_text])
                    else:
                        self.set_text([""])
                        if orig_text not in renplay_pending:
                            renplay_pending.add(orig_text)
                            clean, tags = hide_tags(orig_text)
                            if is_translatable(clean):
                                renplay_tag_map[clean] = (orig_text, tags)
                                Translator.requestTranslation(clean)
                            else:
                                renplay_trans_cache[orig_text] = orig_text
                                self.set_text([orig_text])
            except Exception:
                pass
                
        renpy.text.text.Text.__init__ = renplay_new_text_init
        
        def renplay_check_translations():
            try:
                raw_java = Translator.getTranslatedItemsStr()
                if raw_java:
                    raw = str(raw_java)
                    items = raw.split('|||')
                    has_new = False
                    for i in range(0, len(items) - 1, 2):
                        orig_clean = items[i]
                        trans_clean = items[i+1]
                        if orig_clean in renplay_tag_map:
                            real_orig, tags = renplay_tag_map[orig_clean]
                            final_trans = restore_tags(trans_clean, tags)
                            if real_orig not in renplay_trans_cache:
                                renplay_trans_cache[real_orig] = final_trans
                                has_new = True
                    if has_new:
                        for t in list(renplay_tracked_texts):
                            try:
                                if hasattr(t, 'renplay_orig_text') and t.renplay_orig_text in renplay_trans_cache:
                                    trans_text = renplay_trans_cache[t.renplay_orig_text]
                                    if getattr(t, 'renplay_substitute', False):
                                        scope = getattr(t, 'renplay_scope', None)
                                        try:
                                            import renpy.substitutions
                                            trans_text, _ = renpy.substitutions.substitute(trans_text, scope)
                                        except Exception:
                                            pass
                                    t.set_text([trans_text])
                                    renpy.display.render.redraw(t, 0)
                            except Exception:
                                pass
            except Exception:
                pass

screen renplay_translator_overlay():
    zorder 100
    if getattr(store, "renplay_trans_enabled", False):
        timer 0.1 repeat True action Function(renplay_check_translations)

init python:
    if getattr(store, "renplay_trans_enabled", False):
        config.always_shown_screens.append('renplay_translator_overlay')