import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import { Link } from 'react-router';
import 'katex/dist/katex.min.css';
import { assetUrl } from '../api/wikiApi';

const VIDEO_FILE = /\.(mp4|webm)(\?|$)/i;
const VIDEO_ASSET = /name=[^&]*\.(mp4|webm)/i;

// Lezione 12: il Markdown della wiki.
// - remark-gfm: tabelle, liste di controllo, testo barrato
// - remark-math + rehype-katex: formule LaTeX ($...$ e $$...$$)
// - NIENTE rehype-raw: l'HTML eventualmente presente nelle pagine NON viene interpretato (lezione 8)
function buildComponents(allowImages) {
  return {
    a({ href = '', children }) {
      // Collegamenti interni (convertiti dal backend): React Router, senza ricaricare la pagina
      if (href.startsWith('/wiki/')) {
        return <Link to={href}>{children}</Link>;
      }
      return (
        <a href={href} target="_blank" rel="noopener noreferrer nofollow">
          {children}
        </a>
      );
    },
    img({ src = '', alt = '' }) {
      if (!allowImages) return null;
      const url = assetUrl(src);
      if (VIDEO_FILE.test(src) || VIDEO_ASSET.test(src)) {
        return <video src={url} controls preload="metadata" />;
      }
      return <img src={url} alt={alt} loading="lazy" />;
    },
  };
}

const PAGE_COMPONENTS = buildComponents(true);
// Nelle risposte del modello niente immagini: un'immagine con un indirizzo esterno potrebbe
// "esfiltrare" dati nella query string (lo abbiamo visto nella lezione 8)
const ANSWER_COMPONENTS = buildComponents(false);

export default function WikiMarkdown({ markdown, isAnswer = false }) {
  return (
    <div className="wiki-markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={isAnswer ? ANSWER_COMPONENTS : PAGE_COMPONENTS}
      >
        {markdown}
      </ReactMarkdown>
    </div>
  );
}
