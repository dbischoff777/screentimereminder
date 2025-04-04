/**
 * Simple utility to ensure dropdown menus are properly positioned
 */

// Add TypeScript declarations for the window object
declare global {
  interface Window {
    checkDropdownPositions: () => void;
  }
}

// Basic function to ensure dropdowns don't extend beyond viewport
function checkDropdownPositions(): void {
  // Find all dropdowns currently in the DOM
  const dropdowns = document.querySelectorAll('.mantine-Select-dropdown, .custom-dropdown-menu, .mantine-Popover-dropdown');
  
  // Check each dropdown
  dropdowns.forEach(dropdown => {
    const element = dropdown as HTMLElement;
    if (!element || !element.offsetParent) return;
    
    // Get dropdown position information
    const rect = element.getBoundingClientRect();
    const viewportHeight = window.innerHeight;
    const viewportWidth = window.innerWidth;
    
    // Fix horizontal positioning if needed
    if (rect.right > viewportWidth) {
      element.style.left = `${Math.max(0, viewportWidth - rect.width - 10)}px`;
    }
    
    // For upward-opening dropdowns (already handled in the component)
    if (element.classList.contains('position-top')) {
      // These are already positioned correctly
      return;
    }
    
    // Fix vertical positioning for downward-opening dropdowns
    if (rect.bottom > viewportHeight - 60) { // Allow space for possible navbar
      // Either reposition or limit height
      if (rect.top > viewportHeight / 2) {
        // If in lower half of screen, position upward
        element.style.bottom = `${viewportHeight - rect.top}px`;
        element.style.top = 'auto';
        element.classList.add('position-top');
      } else {
        // If in upper half, just limit the height
        const maxHeight = viewportHeight - rect.top - 60;
        element.style.maxHeight = `${maxHeight}px`;
      }
    }
  });
}

// Simple event listeners
window.addEventListener('scroll', () => setTimeout(checkDropdownPositions, 100));
window.addEventListener('resize', () => setTimeout(checkDropdownPositions, 100));
document.addEventListener('click', () => setTimeout(checkDropdownPositions, 100));

// Make it globally available
window.checkDropdownPositions = checkDropdownPositions;

// Run on page load
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => setTimeout(checkDropdownPositions, 300));
} else {
  setTimeout(checkDropdownPositions, 300);
} 